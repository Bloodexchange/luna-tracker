package app.luna.cycle;

import java.time.LocalDate;
import java.util.Arrays;

public final class CoreTest {
    private static int assertions;
    private static void check(boolean condition,String name) { assertions++;if(!condition)throw new AssertionError(name); }
    public static void main(String[] args) {
        char[] password="correct horse battery staple".toCharArray();
        String hash=Core.credential(password);
        check(!hash.contains("correct"),"no plaintext password");
        check(Arrays.equals(password,new char[password.length]),"password input erased after derivation");
        check(Core.verify("correct horse battery staple".toCharArray(),hash),"correct password");
        check(!Core.verify("incorrect".toCharArray(),hash),"wrong password rejected");
        check(!Core.verify("correct horse battery staple".toCharArray(),"broken"),"malformed record rejected");
        check(!hash.equals(Core.credential("correct horse battery staple".toCharArray())),"random salts");
        check(!Core.verify("x".toCharArray(),"99999999:AA==:AA=="),"unbounded KDF rejected");
        LocalDate start=LocalDate.of(2026,1,1);
        check(Core.cycleDay(start,start,28)==1,"first day");
        check(Core.cycleDay(start,start.plusDays(27),28)==28,"last day");
        check(Core.cycleDay(start,start.plusDays(28),28)==1,"new cycle");
        check(Core.cycleDay(start,start.minusDays(1),28)==0,"future start");
        check(Core.nextPeriod(start,start.plusDays(27),28).equals(start.plusDays(28)),"upcoming period");
        check(Core.nextPeriod(start,start.plusDays(28),28).equals(start.plusDays(56)),"next full cycle");
        check(Core.nextPeriod(start,start.minusDays(2),28).equals(start),"future initial period");
        try{Core.cycleDay(start,start,0);throw new AssertionError("invalid length accepted");}catch(IllegalArgumentException expected){assertions++;}
        SessionMonitor session=new SessionMonitor("target",0);
        session.resumed("luna","Main",10);
        check(!session.mustLock(100),"initial source activity does not abort launch");
        session.resumed("target","First",200);
        check(!session.mustLock(5000),"active target stays open");
        session.paused("target","First",5100);
        check(!session.mustLock(5300),"short activity transition grace");
        session.resumed("target","Second",5350);
        session.paused("target","First",5400);
        check(!session.mustLock(5900),"old activity stop does not lock new target activity");
        session.paused("target","Second",6000);
        check(session.mustLock(6400),"backgrounded target locks");
        SessionMonitor switched=new SessionMonitor("target",0);switched.resumed("target","First",100);switched.resumed("other","Other",200);
        check(switched.mustLock(200),"different foreground app locks immediately");
        SessionMonitor missing=new SessionMonitor("target",0);
        check(missing.mustLock(8000),"no foreground evidence times out closed");
        System.out.println("PASS: "+assertions+" authentication, cycle and session assertions");
    }
}
