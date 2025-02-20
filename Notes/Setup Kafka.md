Download Kafka: Go to the Apache Kafka download page and download the latest binary (.tgz).

Extract the Archive:

```
tar -xzf kafka_2.13-<version>.tgz
cd kafka_2.13-<version>
```

Start Zookeeper: Kafka requires Zookeeper, which is bundled with Kafka.

```
bin/zookeeper-server-start.sh config/zookeeper.properties
```

This will start a Zookeeper service required for Kafka.

Start Kafka Broker:

Open a new terminal window and navigate to the Kafka directory again:

```
bin/kafka-server-start.sh config/server.properties
```

Create a Topic:

Open another terminal window to create a new topic:

```
bin/kafka-topics.sh --create --topic test --bootstrap-server localhost:9092 --replication-factor 1 --partitions 1
```

Send Messages:

In another terminal window, start a producer to send messages to your topic:

```
bin/kafka-console-producer.sh --topic test --bootstrap-server localhost:9092 
```

You can type messages and hit enter to send them.

```shell
kafka-console-producer.sh --topic test --bootstrap-server localhost:9092 < mot.json
```

Consume Messages:

Finally, open another terminal to start a consumer to read messages from your topic:

```
kafka-console-consumer.sh --topic test --bootstrap-server localhost:9092 --from-beginning
```

[Connector Docs](https://kafka.apache.org/quickstart)

```
connect-standalone.sh $KAFKADIR/config/connect-standalone.properties /Users/jlp/Documents/Source/Kafka/kafka_2.13-3.9.0/config/mot-sink.properties 
```

Had to also disable JSON schema validation.

```
connect-standalone.sh $KAFKADIR/connect-standalone-jlp.properties $KAFKADIR/MongoSinkConnector.properties
```