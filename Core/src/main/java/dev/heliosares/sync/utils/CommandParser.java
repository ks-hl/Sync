package dev.heliosares.sync.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CommandParser {

    public static List<String> tab(List<String> out, String currentArg) {
        if (out == null) return new ArrayList<>();
        return out.stream().filter((s) -> s != null && s.toLowerCase().startsWith(currentArg)).collect(Collectors.toList());
    }

    public static Result parse(String key, String cmd) {
        String[] args = cmd.split(" ");
        String value = null;
        int start = 0;

        if (args.length >= 2 && args[0].equalsIgnoreCase(key)) {
            value = args[1];
            start = 2;
        }

        return new Result(concat(start, args), value);
    }

    public static String concat(int start, String... args) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (!out.isEmpty()) out.append(" ");
            out.append(args[i]);
        }
        return out.toString();
    }

    public record Result(String remaining, String value) {
    }
}
