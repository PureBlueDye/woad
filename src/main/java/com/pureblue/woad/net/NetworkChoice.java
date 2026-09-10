package com.pureblue.woad.net;

import com.pureblue.woad.core.Woad;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which network adapter Minecraft should leave through, when the machine has several.
 *
 * <p>There is no API to "pick an interface": the socket is instead <em>bound</em> to that adapter's
 * local IPv4 address before connecting, and the routing table then sends the packets out of it.
 * {@code ConnectionBindMixin} applies the choice; this class only decides what it is.
 *
 * <p>The adapter is remembered by name rather than by address, so a DHCP lease change does not
 * silently fall back to another card.
 */
public final class NetworkChoice {

    private static final Logger LOGGER = LoggerFactory.getLogger("Woad");

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve(Woad.MOD_ID).resolve("network.txt");

    /** The stored adapter name, or empty for "let the system decide". */
    private static volatile String selected = read();

    private NetworkChoice() {}

    /**
     * One pickable adapter.
     *
     * @param label    short, address-free name shown on the button — safe on a shared screen
     * @param fullName the adapter's full description, for the tooltip only
     * @param address  the local IPv4 to bind to, {@code null} for the automatic entry
     */
    public record Option(String id, String label, String fullName, InetAddress address) {}

    /**
     * The adapters worth offering: up, not loopback, not virtual, and holding an IPv4 address.
     * The automatic entry always comes first.
     */
    public static List<Option> options() {
        List<Option> options = new ArrayList<>();
        options.add(new Option("", "Automatic", "Automatic", null));
        try {
            List<String> taken = new ArrayList<>();
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                InetAddress address = ipv4Of(nic);
                if (address == null) continue;

                // The button must be safe to show on stream, so it carries a brand name rather
                // than an address. Duplicates get numbered so two cards stay distinguishable.
                String brand = brandOf(nic);
                String unique = brand;
                for (int n = 2; taken.contains(unique); n++) {
                    unique = brand + " " + n;
                }
                taken.add(unique);
                options.add(new Option(nic.getName(), unique, fullName(nic), address));
            }
        } catch (SocketException e) {
            LOGGER.warn("[Woad] could not list network adapters", e);
        }
        return options;
    }

    /** The adapter currently chosen, falling back to automatic if it has gone away. */
    public static Option current() {
        List<Option> options = options();
        for (Option option : options) {
            if (option.id().equals(selected)) return option;
        }
        return options.get(0);
    }

    /** Moves to the next adapter in the list and remembers it. */
    public static void cycle() {
        List<Option> options = options();
        int index = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id().equals(selected)) {
                index = i;
                break;
            }
        }
        selected = options.get((index + 1) % options.size()).id();
        write(selected);
        LOGGER.info("[Woad] network adapter set to '{}'", selected.isEmpty() ? "automatic" : selected);
    }

    /**
     * The local address to bind outgoing connections to.
     *
     * @return the chosen adapter's IPv4, or {@code null} to leave the choice to the system
     */
    public static InetAddress boundAddress() {
        String id = selected;
        if (id.isEmpty()) return null;
        try {
            NetworkInterface nic = NetworkInterface.getByName(id);
            // Unplugged or renamed: better to connect normally than to fail outright.
            if (nic == null || !nic.isUp()) return null;
            return ipv4Of(nic);
        } catch (SocketException e) {
            return null;
        }
    }

    private static InetAddress ipv4Of(NetworkInterface nic) {
        for (InetAddress address : Collections.list(nic.getInetAddresses())) {
            if (address instanceof Inet4Address && !address.isLoopbackAddress()) return address;
        }
        return null;
    }

    /** The maker's name — "Realtek PCIe 2.5GbE Family Controller" becomes "Realtek". */
    private static String brandOf(NetworkInterface nic) {
        String name = fullName(nic);
        String first = name.split("[\\s(]+")[0];
        return first.isBlank() ? nic.getName() : first;
    }

    /** Full adapter description, only ever shown in the button's tooltip. */
    private static String fullName(NetworkInterface nic) {
        String name = nic.getDisplayName();
        if (name == null || name.isBlank()) name = nic.getName();
        return name.length() <= 40 ? name : name.substring(0, 39) + "…";
    }

    private static String read() {
        try {
            return Files.exists(FILE) ? Files.readString(FILE, StandardCharsets.UTF_8).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static void write(String id) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, id, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[Woad] could not save the network adapter choice", e);
        }
    }
}
