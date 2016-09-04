package com.ittera.cometa.util;

import com.ittera.cometa.distributor.messages.data.Wrappers;
import com.ittera.cometa.distributor.messages.data.DataMessageFactory;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.FileInputStream;
import java.io.IOException;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;

import java.lang.System;

import org.apache.commons.lang3.StringUtils;

public class AppLauncher {

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

    String distributorId = properties.getProperty("id");
    String kafkaTopic = properties.getProperty("kafkaTopic");


    /** Configure and Initialize Kafka Producer **/
    final Properties kafkaProducerProps = new Properties();
    //common kafka properties
    for (String propKey: properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.") && ! (propKey.startsWith("kafka.consumer") || propKey.startsWith("kafka.producer")) ) {
        kafkaProducerProps.put(StringUtils.substringAfter(propKey,"kafka."),properties.getProperty(propKey));
      }
    }
    //producer properties
    for (String propKey: properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.producer.")) {
        kafkaProducerProps.put(StringUtils.substringAfter(propKey,"kafka.producer."),properties.getProperty(propKey));
      }
    }
    //other producer specific props
    kafkaProducerProps.put("client.id", String.valueOf(distributorId));
    final KafkaProducer producer = new KafkaProducer<>(kafkaProducerProps);

    //now read in from stdin, wrap calls in messages and send them
    Scanner stdin = new Scanner(System.in);
    while(stdin.hasNextLine()) {
      String[] lineParts = stdin.nextLine().trim().split(" ");
      String className = lineParts[0];
      String[] mainArgs = Arrays.copyOfRange(lineParts, 1, lineParts.length);
      String methodName = "main";
      int modifiers = Modifier.PUBLIC | Modifier.STATIC;
      Class returnType = Void.class;
      Class[] parameterTypes = new Class[]{String[].class};
      Object[] parameters = new Object[]{mainArgs};
      final Wrappers.DataMessage msg = DataMessageFactory.buildClassMethodMessage(distributorId, className, methodName, modifiers, returnType, parameterTypes, parameters);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));

    System.out.println("Sent message:\n"+msg.toString());
    }
  }
}
