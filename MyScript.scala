import eu.wisebed.client.WisebedClient

object MyScript extends App with WisebedClient {
  args.foreach(arg => println(arg))
}

MyScript.main(args)
