package com.ittera.cometa.util;

import com.ittera.cometa.distributor.messages.data.Wrappers;
import com.ittera.cometa.distributor.messages.data.DataMessageFactory;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;

import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;

import java.lang.System;

import org.apache.commons.lang3.StringUtils;


public class CommandLineClient {

  private final String clientId;
  private final String kafkaTopic;
  private final Properties kafkaProducerProps = new Properties();
  final KafkaProducer producer;

  public CommandLineClient(Properties properties) {

    clientId = properties.getProperty("id");
    kafkaTopic = properties.getProperty("kafkaTopic");

    /** Configure and Initialize Kafka Producer **/
    for (String propKey : properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.producer.")) {
        kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka.producer."), properties.getProperty(propKey));
      } else if (propKey.startsWith("kafka.")) {
        kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka."), properties.getProperty(propKey));
      }
    }

    //other producer specific props
    kafkaProducerProps.put("client.id", String.valueOf(clientId));
    producer = new KafkaProducer<>(kafkaProducerProps);
  }

  private Object typeObject(String className, String value) throws Exception {
    Class clazz = Class.forName(className);
    Constructor constructor = clazz.getConstructor(String.class);
    return constructor.newInstance(value);
  }

  private Wrappers.DataMessage parseMessage(String line) {
    String[] lineParts = line.trim().split(" ");

    String className = lineParts[0];

    if ("new".equals(lineParts[1])) {
      /** example: com.ittera.cometa.demos.App new */
      return DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
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
      return DataMessageFactory.buildClassMethodMessage(clientId, className, methodName, modifiers, returnType, parameterTypesNamesArray, parameters);
    } else if ("get".equals(lineParts[1])) {
      if (lineParts.length == 4) {
        /** get field - example: com.ittera.cometa.demos.App get object-ref anInstanceVar */
        String objectRef = lineParts[2];
        String fieldName = lineParts[3];

        return DataMessageFactory.buildGetObjectMessage(clientId, className, fieldName, objectRef);
      }
      /** get static - example: com.ittera.cometa.demos.App get aClassVar */
      String fieldName = lineParts[2];
      return DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    } else if ("set".equals(lineParts[1])) {
      if (lineParts.length == 5) {
        /** set instance - example: com.ittera.cometa.demos.App set object-ref anInstanceVar ref/class:value */
        String objectRef = lineParts[2];
        String fieldName = lineParts[3];
        String valuePart = lineParts[4];
        if (valuePart.startsWith("ref")) {
          String valueOjectRef = StringUtils.substringAfter(valuePart, "ref:");
          return DataMessageFactory.buildPutObjectMessage(clientId, className, fieldName, objectRef, valueOjectRef);
        } else { //we assume is primitive or string
          String classAbbrev = StringUtils.substringBefore(valuePart, ":");
          String valueClassName = "java.lang." + StringUtils.capitalize(classAbbrev);
          String valueAsString = StringUtils.substringAfter(valuePart, ":");
          final Object value;
          try {
            value = typeObject(valueClassName, valueAsString);
          } catch (Exception ex) {
            ex.printStackTrace();
            return null;
          }
          return DataMessageFactory.buildPutObjectMessage(clientId, className, fieldName, objectRef, valueClassName, value);
        }
      }
      /** set static - example: com.ittera.cometa.demos.App set aClassVar ref/class:value */
      String fieldName = lineParts[2];
      String valuePart = lineParts[3];
      if (valuePart.startsWith("ref")) {
        String objectRef = StringUtils.substringAfter(valuePart, "ref:");
        return DataMessageFactory.buildPutStaticMessage(clientId, className, fieldName, objectRef);
      } else {  //we assume is primitive or string
        String classAbbrev = StringUtils.substringBefore(valuePart, ":");
        String valueClassName = "java.lang." + StringUtils.capitalize(classAbbrev);
        String valueAsString = StringUtils.substringAfter(valuePart, ":");
        final Object value;
        try {
          value = typeObject(valueClassName, valueAsString);
        } catch (Exception ex) {
          ex.printStackTrace();
          return null;
        }
        return DataMessageFactory.buildPutStaticMessage(clientId, className, fieldName, valueClassName, value);
      }
    } else if ("instance".equals(lineParts[1])) {
      /** example: com.ittera.cometa.demos.App instance object-ref someInstanceMethod */
      String objectRef = lineParts[2];
      String methodName = lineParts[3];

      return DataMessageFactory.buildInstanceMethodMessage(clientId, className, methodName, objectRef, new String[]{}, new Object[]{});
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

    CommandLineClient launcher = new CommandLineClient(properties);
    launcher.repl(System.in);

  }
}
