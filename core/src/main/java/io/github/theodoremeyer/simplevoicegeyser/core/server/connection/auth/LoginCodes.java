package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.LongSupplier;

/** Short-lived, single-use codes delivered only to the online game session. */
public final class LoginCodes {
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final LongSupplier clock;
    public LoginCodes() { this(System::currentTimeMillis); }
    LoginCodes(LongSupplier clock) { this.clock = clock; }
    private static final class Entry {
        final SvgPlayer player; final String code; final long expires;
        long sent; int attempts; boolean used;
        Entry(SvgPlayer p, String c, long now) { player=p; code=c; expires=now+120000; sent=now; }
    }
    public synchronized boolean send(SvgPlayer player) {
        long now=clock.getAsLong();
        entries.values().removeIf(e -> now>=e.expires || !e.player.isOnline());
        Entry entry=entries.get(player.getUniqueId());
        if (entry!=null && entry.player!=player) { entries.remove(player.getUniqueId()); entry=null; }
        if (entry!=null && (now-entry.sent<30000 || entry.used || entry.attempts>=5)) return false;
        if (entry==null) {
            if (entries.size()>=1024) return false;
            entry=new Entry(player, String.format(Locale.ROOT,"%06d",random.nextInt(1000000)),now);
            entries.put(player.getUniqueId(),entry);
        }
        entry.sent=now;
        player.sendMessage("[CrossTalk] Your voice login code: " + entry.code
                + ". Expires in 2 minutes. Enter it only on your server's voice website. Do not share it. Ignore this if you did not request it.");
        return true;
    }
    public synchronized boolean consume(SvgPlayer player,String code) {
        Entry e=entries.get(player.getUniqueId());
        if (e==null || e.player!=player || !player.isOnline() || clock.getAsLong()>=e.expires || e.used || e.attempts>=5) return false;
        e.attempts++;
        if (code==null || !e.code.equals(code)) return false;
        e.used=true;
        return true;
    }
    public synchronized void invalidate(UUID uuid) { entries.remove(uuid); }
}
