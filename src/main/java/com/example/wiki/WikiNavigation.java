package com.example.wiki;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static com.example.wiki.WikiTypes.*;

/** A deterministic reading aid; it is not a retrieval index or a semantic graph. */
final class WikiNavigation {
    private static final Pattern LINK = Pattern.compile("\\[\\[([a-z0-9가-힣][a-z0-9가-힣-]{0,79})(?:\\|[^\\]\\r\\n]*)?\\]\\]");
    static void write(Path directory, Manifest manifest, Map<String, String> contents) throws IOException {
        var pages = new LinkedHashMap<String, Entry>();
        manifest.pages().forEach(page -> pages.put(page.id(), page));
        var outgoing = new LinkedHashMap<String, Set<String>>();
        var incoming = new LinkedHashMap<String, Set<String>>();
        pages.keySet().forEach(id -> incoming.put(id, new TreeSet<>()));
        for (String id : pages.keySet()) {
            var targets = new TreeSet<String>();
            var matcher = LINK.matcher(contents.get(id));
            while (matcher.find()) {
                String target = matcher.group(1);
                if (pages.containsKey(target)) {
                    targets.add(target);
                    incoming.get(target).add(id);
                }
            }
            outgoing.put(id, targets);
        }
        var text = new StringBuilder("# 위키 탐색\n\n")
                .append("자동 생성된 열람용 목록입니다. 페이지 본문과 manifest를 기준으로 발행할 때 다시 만듭니다.\n")
                .append("링크는 명시된 참조이며 의미적 관계나 사실 검증 결과가 아닙니다. 검색·답변 문맥에는 포함하지 않습니다.\n\n")
                .append("## 페이지\n\n");
        for (var page : pages.values()) {
            text.append("- ").append(link(page)).append("\n");
            text.append("  - 출처: ").append(String.join(", ", page.sourceIds().stream().map(WikiNavigation::label).toList())).append("\n");
            text.append("  - 참조하는 페이지: ").append(links(outgoing.get(page.id()), pages)).append("\n");
            text.append("  - 이 페이지를 참조: ").append(links(incoming.get(page.id()), pages)).append("\n");
        }
        text.append("\n## 출처별 페이지\n\n");
        for (Source source : manifest.sources()) {
            var related = new TreeSet<String>();
            pages.values().stream().filter(p -> p.sourceIds().contains(source.id())).forEach(p -> related.add(p.id()));
            text.append("- ").append(label(source.id())).append(": ").append(links(related, pages)).append("\n");
        }
        Path folder = directory.resolve("_navigation");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("index.md"), text.toString(), StandardOpenOption.CREATE_NEW);
    }
    private static String links(Set<String> ids, Map<String, Entry> pages) {
        return ids.isEmpty() ? "없음" : String.join(", ", ids.stream().map(id -> link(pages.get(id))).toList());
    }
    private static String link(Entry page) {
        return "[" + label(page.title()) + "](../" + page.id() + ".md)";
    }
    private static String label(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\\", "&#92;").replace("[", "&#91;").replace("]", "&#93;")
                .replace("*", "&#42;").replace("_", "&#95;").replace(String.valueOf((char) 96), "&#96;")
                .replace("\r", " ").replace("\n", " ");
    }
}
