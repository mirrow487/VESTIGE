package dev.vestige.waylight;

/** Client-side handler for the menu packet (installed by the client; a no-op on dedicated servers). */
public interface WaylightClientSink {
    WaylightClientSink NONE = p -> {};

    void open(WaylightPayloads.Open payload);
}
