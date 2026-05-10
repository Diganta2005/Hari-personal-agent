package com.HARI.HARI;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private static final Path DATA_FILE = Path.of("hari-data", "memory.txt");

    private final List<String> notes = new ArrayList<>();
    private final List<String> tasks = new ArrayList<>();
    private final Map<String, String> memories = new LinkedHashMap<>();
    private final OpenAiService openAiService;

    public AgentService(OpenAiService openAiService) {
        this.openAiService = openAiService;
        loadMemory();
    }

    public String reply(String message) {
        if (message == null || message.isBlank()) {
            return "I am listening. Tell me what you want to do.";
        }

        String cleanMessage = message.trim();
        String lowerMessage = cleanMessage.toLowerCase(Locale.ROOT);

        if (lowerMessage.equals("help") || lowerMessage.contains("what can you do")) {
            return help();
        }

        if (lowerMessage.equals("time") || lowerMessage.equals("date") || lowerMessage.contains("what time is it")) {
            return currentTime();
        }

        if (lowerMessage.startsWith("remember ")) {
            return remember(cleanMessage);
        }

        if (lowerMessage.equals("show memory") || lowerMessage.equals("what do you remember")) {
            return showMemory();
        }

        if (lowerMessage.startsWith("save note ") || lowerMessage.startsWith("note ")) {
            return saveNote(cleanMessage);
        }

        if (lowerMessage.equals("show notes") || lowerMessage.equals("my notes")) {
            return showList("notes", notes);
        }

        if (lowerMessage.startsWith("delete note ")) {
            return removeItem(cleanMessage, "delete note", notes, "note");
        }

        if (lowerMessage.startsWith("add task ") || lowerMessage.startsWith("todo ")) {
            return addTask(cleanMessage);
        }

        if (lowerMessage.equals("show tasks") || lowerMessage.equals("my tasks")) {
            return showList("tasks", tasks);
        }

        if (lowerMessage.startsWith("complete task ") || lowerMessage.startsWith("delete task ")) {
            String command = lowerMessage.startsWith("complete task ") ? "complete task" : "delete task";
            return removeItem(cleanMessage, command, tasks, "task");
        }

        if (lowerMessage.startsWith("calculate ") || looksLikeMath(lowerMessage)) {
            return calculate(cleanMessage);
        }

        if (lowerMessage.contains("hello") || lowerMessage.contains("hi")) {
            return "Hello. I am Hari, your personal agent. I can remember things, save notes, track tasks, and use tools.";
        }

        return openAiService.ask(cleanMessage, agentContext());
    }

    private String help() {
        return """
                I can help with these commands:
                remember my goal is Build an agentic AI
                show memory
                save note I am learning Java
                show notes
                delete note 1
                add task Build Hari memory
                show tasks
                complete task 1
                calculate (12 + 8) * 3
                calculate 20% of 450
                calculate 2^8 + sqrt(81)
                time

                If your OpenAI API key is set, I can also answer normal questions using a real AI model.
                """;
    }

    private String currentTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
        return "Current local time: " + LocalDateTime.now().format(formatter);
    }

    private String remember(String message) {
        String value = message.replaceFirst("(?i)^remember\\s+", "").trim();
        Matcher matcher = Pattern.compile("(?i)^(?:my\\s+)?(.+?)\\s+is\\s+(.+)$").matcher(value);

        if (!matcher.matches()) {
            return "Use this format: remember my goal is Build Hari";
        }

        String key = matcher.group(1).trim().toLowerCase(Locale.ROOT);
        String memory = matcher.group(2).trim();

        if (key.isBlank() || memory.isBlank()) {
            return "Use this format: remember my goal is Build Hari";
        }

        memories.put(key, memory);
        saveMemory();
        return "I will remember your " + key + ": " + memory;
    }

    private String showMemory() {
        if (memories.isEmpty()) {
            return "I do not have any saved memories yet.";
        }

        StringBuilder response = new StringBuilder("Here is what I remember:\n");
        memories.forEach((key, value) -> response.append("- ").append(key).append(": ").append(value).append("\n"));
        return response.toString();
    }

    private String saveNote(String message) {
        String note = message.replaceFirst("(?i)^save note\\s+", "").replaceFirst("(?i)^note\\s+", "").trim();

        if (note.isBlank()) {
            return "Write the note after the command. Example: save note Learn agent design";
        }

        notes.add(note);
        saveMemory();
        return "Saved your note: " + note;
    }

    private String addTask(String message) {
        String task = message.replaceFirst("(?i)^add task\\s+", "").replaceFirst("(?i)^todo\\s+", "").trim();

        if (task.isBlank()) {
            return "Write the task after the command. Example: add task Connect Hari to an AI model";
        }

        tasks.add(task);
        saveMemory();
        return "Added your task: " + task;
    }

    private String showList(String title, List<String> items) {
        if (items.isEmpty()) {
            return "You do not have any saved " + title + " yet.";
        }

        StringBuilder response = new StringBuilder("Your " + title + ":\n");
        for (int i = 0; i < items.size(); i++) {
            response.append(i + 1).append(". ").append(items.get(i)).append("\n");
        }
        return response.toString();
    }

    private String removeItem(String message, String command, List<String> items, String itemName) {
        String numberText = message.replaceFirst("(?i)^" + Pattern.quote(command) + "\\s+", "").trim();

        try {
            int index = Integer.parseInt(numberText) - 1;
            if (index < 0 || index >= items.size()) {
                return "I could not find " + itemName + " number " + numberText + ".";
            }

            String removed = items.remove(index);
            saveMemory();
            return "Removed " + itemName + ": " + removed;
        } catch (NumberFormatException error) {
            return "Use a number. Example: " + command + " 1";
        }
    }

    private String calculate(String message) {
        String originalExpression = message.replaceFirst("(?i)^calculate\\s+", "").trim();
        String expression = normalizeMathExpression(originalExpression);

        if (expression.isBlank()) {
            return "Try a calculation like: calculate (12 + 8) * 3";
        }

        try {
            double result = new MathParser(expression).parse();
            return originalExpression + " = " + formatNumber(result);
        } catch (ArithmeticException | IllegalArgumentException error) {
            return "I could not calculate that: " + error.getMessage();
        }
    }

    private boolean looksLikeMath(String message) {
        return message.matches(".*\\d+\\s*(\\+|-|\\*|/|\\^|%|plus|minus|times|multiplied|divided)\\s*\\d+.*");
    }

    private String normalizeMathExpression(String expression) {
        return expression.toLowerCase(Locale.ROOT)
                .replaceAll(",", "")
                .replaceAll("\\bplus\\b", "+")
                .replaceAll("\\bminus\\b", "-")
                .replaceAll("\\btimes\\b", "*")
                .replaceAll("\\bmultiplied by\\b", "*")
                .replaceAll("\\bmultiplied\\b", "*")
                .replaceAll("\\bdivide by\\b", "/")
                .replaceAll("\\bdivided by\\b", "/")
                .replaceAll("\\bdivided\\b", "/")
                .replaceAll("\\binto\\b", "*")
                .replaceAll("\\bof\\b", "*")
                .replaceAll("\\bsquare root of\\b", "sqrt")
                .replaceAll("\\bsquare root\\b", "sqrt")
                .replaceAll("\\bpower\\b", "^")
                .replaceAll("\\bto the power of\\b", "^")
                .replaceAll("\\bpi\\b", String.valueOf(Math.PI));
    }

    private String formatNumber(double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("result is not a finite number");
        }

        if (Math.abs(value - Math.rint(value)) < 0.0000000001) {
            return String.valueOf((long) Math.rint(value));
        }

        String text = String.format(Locale.ROOT, "%.10f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String agentContext() {
        StringBuilder context = new StringBuilder("Hari local context:\n");

        if (memories.isEmpty()) {
            context.append("Saved memory: none\n");
        } else {
            context.append("Saved memory:\n");
            memories.forEach((key, value) -> context.append("- ").append(key).append(": ").append(value).append("\n"));
        }

        context.append("Notes count: ").append(notes.size()).append("\n");
        context.append("Tasks count: ").append(tasks.size()).append("\n");
        return context.toString();
    }

    private void loadMemory() {
        if (!Files.exists(DATA_FILE)) {
            return;
        }

        try {
            for (String line : Files.readAllLines(DATA_FILE, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\t", 3);
                if (parts.length < 2) {
                    continue;
                }

                String type = parts[0];
                String firstValue = decode(parts[1]);

                if (type.equals("NOTE")) {
                    notes.add(firstValue);
                } else if (type.equals("TASK")) {
                    tasks.add(firstValue);
                } else if (type.equals("MEMORY") && parts.length == 3) {
                    memories.put(firstValue, decode(parts[2]));
                }
            }
        } catch (IOException error) {
            System.err.println("Hari could not load memory: " + error.getMessage());
        }
    }

    private void saveMemory() {
        List<String> lines = new ArrayList<>();

        for (String note : notes) {
            lines.add("NOTE\t" + encode(note));
        }

        for (String task : tasks) {
            lines.add("TASK\t" + encode(task));
        }

        memories.forEach((key, value) -> lines.add("MEMORY\t" + encode(key) + "\t" + encode(value)));

        try {
            Files.createDirectories(DATA_FILE.getParent());
            Files.write(DATA_FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException error) {
            System.err.println("Hari could not save memory: " + error.getMessage());
        }
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
