package com.HARI.HARI;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

@Service
public class RagService {

    private static final Path KNOWLEDGE_DIR = Path.of("knowledge");
    private static final Pattern WORD_PATTERN = Pattern.compile("[^a-z0-9]+");

    public String search(String query) {
        List<SearchResult> results = findRelevantChunks(query, 4);

        if (results.isEmpty()) {
            return """
                    I could not find anything relevant in Hari's local knowledge base.
                    Add .txt or .md files inside the knowledge folder, then ask again.
                    """;
        }

        StringBuilder response = new StringBuilder("I found this in Hari's knowledge base:\n\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            response.append(i + 1)
                    .append(". Source: ")
                    .append(result.source())
                    .append("\n")
                    .append(result.chunk())
                    .append("\n\n");
        }

        return response.toString().trim();
    }

    public String buildContext(String query) {
        List<SearchResult> results = findRelevantChunks(query, 5);

        if (results.isEmpty()) {
            return "No relevant local knowledge found.";
        }

        StringBuilder context = new StringBuilder("Relevant local knowledge:\n");
        for (SearchResult result : results) {
            context.append("Source: ")
                    .append(result.source())
                    .append("\n")
                    .append(result.chunk())
                    .append("\n\n");
        }

        return context.toString().trim();
    }

    private List<SearchResult> findRelevantChunks(String query, int limit) {
        if (query == null || query.isBlank() || !Files.isDirectory(KNOWLEDGE_DIR)) {
            return List.of();
        }

        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();

        try (Stream<Path> files = Files.walk(KNOWLEDGE_DIR)) {
            files.filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .forEach(path -> addFileResults(path, queryTerms, results));
        } catch (IOException error) {
            return List.of();
        }

        return results.stream()
                .sorted(Comparator.comparingInt(SearchResult::score).reversed())
                .limit(limit)
                .toList();
    }

    private void addFileResults(Path path, Set<String> queryTerms, List<SearchResult> results) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            for (String chunk : splitIntoChunks(content)) {
                int score = score(chunk, queryTerms);
                if (score > 0) {
                    results.add(new SearchResult(path.getFileName().toString(), chunk.trim(), score));
                }
            }
        } catch (IOException ignored) {
            // Ignore unreadable knowledge files and continue searching the rest.
        }
    }

    private List<String> splitIntoChunks(String content) {
        String cleanContent = content.replace("\r", "").trim();
        if (cleanContent.isBlank()) {
            return List.of();
        }

        String[] paragraphs = cleanContent.split("\\n\\s*\\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() > 900 && !current.isEmpty()) {
                chunks.add(current.toString());
                current.setLength(0);
            }

            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }

    private int score(String chunk, Set<String> queryTerms) {
        Set<String> chunkTerms = tokenize(chunk);
        int score = 0;

        for (String term : queryTerms) {
            if (chunkTerms.contains(term)) {
                score += term.length() > 4 ? 3 : 1;
            }
        }

        return score;
    }

    private Set<String> tokenize(String text) {
        String[] words = WORD_PATTERN.split(text.toLowerCase(Locale.ROOT));
        Set<String> terms = new HashSet<>();

        for (String word : words) {
            if (word.length() > 2 && !isStopWord(word)) {
                terms.add(word);
            }
        }

        return terms;
    }

    private boolean isStopWord(String word) {
        return Set.of("the", "and", "for", "are", "you", "your", "with", "that", "this", "what", "how", "why")
                .contains(word);
    }

    private boolean isSupportedFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".txt") || fileName.endsWith(".md");
    }

    private record SearchResult(String source, String chunk, int score) {
    }
}
