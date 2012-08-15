# WISEBED Scala Client
A Scala client implementation for the WISEBED testbeds, similar to scripting-client and experimentation-scripts.

## Building
The WISEBED Scala Client can be build using Maven only. Please run ```mvn package``` to build a jar that contains base & utility classes, as well as some predefined experimentation scripts.

## Running
Simply invoke the Scala runtime and pass the classpath and the class name of the script you want to execute. Example:

```
scala -cp target/scala-client-1.0-SNAPSHOT-jar-with-dependencies.jar eu.wisebed.client.Reserve
```


## Running custom scripts
The WISEBED Scala Client comes with a set of base classes/traits that can be extended in order to built short and concise user-defined scripts that can be executed using the Scala interpreter. Below is an example of a user-defined script ```MyScript```:

```
import eu.wisebed.client.WisebedClient

object MyScript extends App with WisebedClient {
  args.foreach(arg => println(arg))
}

MyScript.main(args)
```

Please note the last line that has to be present in order to invoke the ```main``` method of the ```MyScript``` object when run using the Scala interpreter. You can then run the script by invoking:

```
scala -cp target/scala-client-1.0-SNAPSHOT-jar-with-dependencies.jar MyScript.scala hello wisebed client
```

which will deliver the expected output:

```
hello
wisebed
client
```
