package io.mazenmc.skypebot.engine.bot;

import io.mazenmc.skypebot.SkypeBot;
import io.mazenmc.skypebot.utils.Resource;
import io.mazenmc.skypebot.utils.Utils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.reflections.Reflections;
import sun.reflect.MethodAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModuleManager {

    private static HashMap<String, CommandData> allCommands = new HashMap<>();
    private static HashMap<String, CommandData> commandData = new HashMap<>();

    private static long lastCommand = 0L;

    private static String executeCommand(String message, CommandData data, Matcher m) {
        if (data.getCommand().cooldown() > 0) {
            if (!SkypeBot.getInstance().getCooldownHandler().canUse(data.getCommand())) {
                return "Command is cooling down! Time Left: " + SkypeBot.getInstance().getCooldownHandler().getCooldownLeft(data.getCommand().name());
            }
        }

        long difference = System.currentTimeMillis() - lastCommand;

        if (difference <= 5000L) {
            if (difference <= 4000L) {
                return "Woah, slow down there bud. Try again in " + TimeUnit.MILLISECONDS.toSeconds(2000L - difference) + " second(s)";
            }
            return "";
        }

        List<Object> a = new ArrayList<>();
        a.add(message);

        if (m.groupCount() > 0) {
            for (int i = 1; i <= m.groupCount(); i++) {
                String g = m.group(i);
                if (g.contains(".") && Utils.isDouble(g)) {
                    a.add(Double.parseDouble(g));
                } else if (Utils.isInteger(g)) {
                    a.add(Integer.parseInt(g));
                } else {
                    a.add(g);
                }
            }
        }

        if (a.size() < data.getMethod().getParameterCount()) {
            for (int i = a.size(); i < data.getMethod().getParameterCount(); i++) {
                if (data.getMethod().getParameters()[i].getType().equals(String.class)) {
                    a.add(null);
                } else {
                    a.add(0);
                }
            }
        }

        MethodAccessor methodAccessor = null;

        try {
            Field methodAccessorField = Method.class.getDeclaredField("methodAccessor");
            methodAccessorField.setAccessible(true);
            methodAccessor = (MethodAccessor) methodAccessorField.get(data.getMethod());

            if (methodAccessor == null) {
                Method acquireMethodAccessorMethod = Method.class.getDeclaredMethod("acquireMethodAccessor", null);
                acquireMethodAccessorMethod.setAccessible(true);
                methodAccessor = (MethodAccessor) acquireMethodAccessorMethod.invoke(data.getMethod(), null);

                lastCommand = System.currentTimeMillis();
            }
        } catch (NoSuchFieldException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            return "Failed... (" + ExceptionUtils.getStackTrace(e) + ")";
        }

        try {
            return methodAccessor.invoke(null, a.toArray()) + " #bot";
        } catch (Exception e) {
            return "Failed... (" + Utils.upload(ExceptionUtils.getStackTrace(e)) + ")";
        }
    }

    public static HashMap<String, CommandData> getCommands() {
        return commandData;
    }

    public static void loadModules(String modulePackage) {
        Reflections r = new Reflections(modulePackage);
        Set<Class<? extends Module>> classes = r.getSubTypesOf(Module.class);

        for (Class<? extends Module> c : classes) {
            for (Method m : c.getMethods()) {
                Command command;
                command = m.getAnnotation(Command.class);

                if (command != null) {
                    CommandData data = new CommandData(command, m);

                    System.out.println("registered " + command.name());

                    commandData.put(command.name(), data);
                    allCommands.put(command.name(), data);
                    if (command.alias() != null && command.alias().length > 0) {
                        for (String s : command.alias()) {
                            allCommands.put(s, data);
                        }
                    }
                }
            }
        }
    }

    public static String parseText(String message) {
        String command = message;

        if (command == null) {
            return null;
        }

        if (command.length() < 1) {
            return null;
        }

        String[] commandSplit = command.split(" ");

        if (commandSplit.length == 0) {
            return null;
        }

        for (Map.Entry<String, CommandData> s : allCommands.entrySet()) {
            String match = s.getKey();

            if (s.getValue().getCommand().command()) {
                match += Resource.COMMAND_PREFIX;
            }

            if (!s.getValue().getParameterRegex(false).equals("")) {
                match += " " + s.getValue().getParameterRegex(false);
            }

            if (s.getValue().getCommand().exact()) {
                match = "^" + match + "$";
            }

            Pattern r = Pattern.compile(match);
            Matcher m = r.matcher(command);

            if (m.find()) {
                return executeCommand(message, s.getValue(), m);
            } else if (!s.getValue().getParameterRegex(false).equals(s.getValue().getParameterRegex(true))) {
                match = s.getKey();
                
                if (s.getValue().getCommand().command()) {
                    match += Resource.COMMAND_PREFIX;
                }

                if (!s.getValue().getParameterRegex(true).equals("")) {
                    match += " " + s.getValue().getParameterRegex(true);
                }

                if (s.getValue().getCommand().exact()) {
                    match = "^" + match + "$";
                }

                r = Pattern.compile(match);
                m = r.matcher(command);
                if (m.find()) {
                    return executeCommand(message, s.getValue(), m);
                }
            }
        }

        if (allCommands.containsKey(commandSplit[0].toLowerCase())) {
            CommandData d = allCommands.get(commandSplit[0].toLowerCase());
            Command c = d.getCommand();

            String correct = commandSplit[0];
            if (!d.getParameterNames().equals("")) {
                correct += " " + d.getParameterNames();
            }

            if (c.command()) {
                if (!message.startsWith(Resource.COMMAND_PREFIX)) {
                    return null;
                }

                correct = Resource.COMMAND_PREFIX + correct;
            }

            return "Incorrect syntax: " + correct;
        }

        if (message.startsWith(Resource.COMMAND_PREFIX)) {
            return "Command '" + commandSplit[0] + "' not found!";
        }

        return null;
    }

}
