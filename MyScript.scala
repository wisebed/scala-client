object MyScript {

  import eu.wisebed.client.WisebedClient

  class MyClass extends App with WisebedClient {

    println(hello())
  }

}

new MyScript.MyClass().main(args)