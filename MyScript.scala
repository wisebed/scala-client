import eu.wisebed.client.WisebedClient

object MyScript extends WisebedClient with App {
  args.foreach(arg => println(arg))
}

MyScript.main(args)
