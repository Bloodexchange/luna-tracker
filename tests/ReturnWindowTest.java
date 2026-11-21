package app.luna.cycle;

public final class ReturnWindowTest {
    private static int assertions;
    private static void check(boolean ok,String name){assertions++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args){
        ReturnWindow window=new ReturnWindow();
        check(!window.expired(5_000_000),"foreground has no deadline");
        window.leave(10_000);
        check(window.remaining(10_000)==120_000,"full two minutes on leaving");
        check(!window.expired(129_999),"return at 1:59.999 is allowed");
        check(window.remaining(129_999)==1,"one millisecond remains");
        check(window.expired(130_000),"return at exactly 2:00 locks");
        check(window.remaining(130_001)==0,"expired duration never negative");
        window.clear();window.leave(200_000);window.leave(260_000);
        check(window.expired(320_000),"duplicate pause does not extend deadline");
        window.clear();
        check(!window.expired(900_000),"return cancels old deadline");
        window.leave(900_000);
        check(!window.expired(1_019_999),"second exit gets its own return window");
        check(window.expired(1_020_000),"second exit expires independently");
        window.clear();window.leave(1_100_000);
        check(window.expired(4_700_000),"elapsed device sleep expires session without timer execution");
        window.clear();window.leave(5_000);
        check(window.expired(4_999),"unexpected monotonic clock reversal locks");
        window.clear();window.leave(0);
        check(window.expired(120_000),"zero elapsed timestamp is valid");
        System.out.println("PASS: "+assertions+" background return-window assertions");
    }
}
