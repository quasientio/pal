package com.ittera.cometa.util;

import com.ittera.cometa.distributor.messages.data.Wrappers;
import com.ittera.cometa.distributor.messages.data.DataMessageFactory;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;

import java.lang.System;

import org.apache.commons.lang3.StringUtils;


public class AppLauncher {

  private final String distributorId;
  private final String kafkaTopic;
  private final Properties kafkaProducerProps = new Properties();
  final KafkaProducer producer;

  public AppLauncher(Properties properties) {

    distributorId = properties.getProperty("id");
    kafkaTopic = properties.getProperty("kafkaTopic");

    /** Configure and Initialize Kafka Producer **/
    //common kafka properties
    for (String propKey : properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.") && !(propKey.startsWith("kafka.consumer") || propKey.startsWith("kafka.producer"))) {
        kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka."), properties.getProperty(propKey));
      }
    }
    //producer properties
    for (String propKey : properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.producer.")) {
        kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka.producer."), properties.getProperty(propKey));
      }
    }
    //other producer specific props
    kafkaProducerProps.put("client.id", String.valueOf(distributorId));
    producer = new KafkaProducer<>(kafkaProducerProps);
  }

  /**
   * Currently only supports:
   * - empty constructor calls - syntax: classname new arg...
   * - calls to a class' main - syntax: classname main arg...
   * - get static - syntax: classname get fieldname
   * -
   *
   * @param line
   * @return
   */
  private Wrappers.DataMessage parseMessage(String line) {
    String[] lineParts = line.trim().split(" ");

    String className = lineParts[0];

    if ("new".equals(lineParts[1])) {
      /** example: com.ittera.cometa.demos.App new */
      return DataMessageFactory.buildEmptyConstructorMessage(distributorId, className);
    } else if ("main".equals(lineParts[1])) {
      /** example: com.ittera.cometa.demos.App main */
      String[] mainArgs = Arrays.copyOfRange(lineParts, 2, lineParts.length);
      String methodName = "main";
      int modifiers = Modifier.PUBLIC | Modifier.STATIC;
      Class returnType = Void.class;
      Class[] parameterTypes = new Class[]{String[].class};
      String[] parameterTypesNamesArray = new String[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; i++) {
        parameterTypesNamesArray[i] = parameterTypes[i].getName();
      }
      Object[] parameters = new Object[]{mainArgs};
      return DataMessageFactory.buildClassMethodMessage(distributorId, className, methodName, modifiers, returnType, parameterTypesNamesArray, parameters);

    } else if ("get".equals(lineParts[1])) {
      /** example: com.ittera.cometa.demos.App get aClassString */
      String fieldname = lineParts[2];

      return DataMessageFactory.buildGetStaticMessage(distributorId, className, fieldname);

    } else if ("instance".equals(lineParts[1])){
      /** example: com.ittera.cometa.demos.App instance object-ref someInstanceMethod */
      String objectRef = lineParts[2];
      String methodName = lineParts[3];
      return DataMessageFactory.buildInstanceMethodMessage(distributorId, className, methodName, objectRef, new String[]{}, new Object[]{});
    } else {
      return null;
    }
  }

  public void repl(InputStream inputStream) {
    Scanner stream = new Scanner(inputStream, "UTF8");
    while (stream.hasNextLine()) {

      final Wrappers.DataMessage msg = parseMessage(stream.nextLine());
      if (msg == null) {
        continue;
      }

      producer.send(new ProducerRecord(kafkaTopic, msg));

      System.out.println("Sent message:\n" + msg.toString());
    }
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Please provide the path to a configuration file");
      System.exit(1);
    }

    Properties properties = new Properties();
    try {
      properties.load(new FileInputStream(args[0]));
    } catch (IOException e) {
      System.err.println("Please provide a valid path to the configuration file");
      e.printStackTrace();
      System.exit(2);
    }

    AppLauncher launcher = new AppLauncher(properties);
    launcher.repl(System.in);

  }
}
