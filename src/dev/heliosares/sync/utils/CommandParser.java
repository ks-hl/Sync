package dev.heliosares.sync.utils;

import java.util.List;
import java.util.stream.Collectors;

public class CommandParser {

    public static List<String> tab(List<String> out, String currentArg) {
        return out.stream().filter((s) -> s.toLowerCase().startsWith(currentArg)).collect(Collectors.toList());
    }

    public static Result parse(String key, String cmd) {
        String[] args = cmd.split(" ");
        String value = null;
        StringBuilder out = new StringBuilder();
        boolean escape = false;
        int i = 0;
        for (; i < args.length; i++) {
            if (i > 0 && (args[i].equalsIgnoreCase("psync") || args[i].equalsIgnoreCase("msync"))) {
                escape = true; // Prevents parsing out parts of the command which are parts of a sub-command
            }
            if (!escape && value == null && args[i].equalsIgnoreCase(key) && i < args.length - 1) {
                value = args[++i];
                continue;
            }
            out.append(args[i]);
            if (i < args.length - 1) {
                out.append(" ");
            }
        }

        return new Result(out.toString(), value);
    }

    public static String concat(int start, String... args) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (out.length() > 0) {
                out.append(" ");
            }
            out.append(args[i]);
        }
        return out.toString();
    }

    public record Result(String remaining, String value) {
    }
}
