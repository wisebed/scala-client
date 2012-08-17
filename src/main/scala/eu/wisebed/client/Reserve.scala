package eu.wisebed.client
import com.google.common.base.Joiner
import eu.wisebed.api.v3.rs.ReservationConflictFault_Exception

object Reserve {
  def main(args: Array[String]) {
    try {
      
      val srks = new ReservationClient(args).reserve()
      val pairs = srks.map(srk => srk.getUrnPrefix() + "," + srk.getSecretReservationKey())
      
      println(pairs.reduceLeft(_ + "," + _))
      
      System.exit(0)
      
    } catch {
      case e: ReservationConflictFault_Exception => {
        System.err.println(e.getFaultInfo().getMessage())
        System.exit(1)
      }
    }
  }
}
