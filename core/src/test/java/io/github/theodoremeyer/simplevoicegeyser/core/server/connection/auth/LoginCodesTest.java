package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class LoginCodesTest {
    static class Player extends BedrockLoginResolverTest.Player {
        String message;
        Player() { super(".Test",true); }
        public void sendMessage(String m) { message=m; }
        String code() { return message.split("code: ")[1].substring(0,6); }
    }
    @Test void privateCodeWorksOnceAndOnlyForItsPlayer() {
        var codes=new LoginCodes(); var p=new Player(); var other=new Player();
        assertTrue(codes.send(p)); assertTrue(p.code().matches("[0-9]{6}"));
        assertFalse(codes.consume(other,p.code()));
        assertTrue(codes.consume(p,p.code())); assertFalse(codes.consume(p,p.code()));
    }
    @Test void expiryResendAndAttemptLimits() {
        var now=new AtomicLong(1000); var codes=new LoginCodes(now::get); var p=new Player();
        assertTrue(codes.send(p)); String first=p.code(); assertFalse(codes.send(p));
        now.addAndGet(30001); assertTrue(codes.send(p)); assertEquals(first,p.code());
        for(int i=0;i<5;i++) assertFalse(codes.consume(p,"wrong"));
        assertFalse(codes.consume(p,first)); now.addAndGet(30001); assertFalse(codes.send(p));
        now.addAndGet(120000); assertTrue(codes.send(p)); now.addAndGet(120000);
        assertFalse(codes.consume(p,p.code()));
    }
    @Test void logoutInvalidatesCode() {
        var codes=new LoginCodes(); var p=new Player(); codes.send(p);
        codes.invalidate(p.id); assertFalse(codes.consume(p,p.code()));
    }
}
