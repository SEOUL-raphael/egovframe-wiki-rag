package com.example.wiki;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static com.example.wiki.WikiTypes.*;
import static org.assertj.core.api.Assertions.*;

class WikiNavigationTest {
    @TempDir Path root;
    private final List<Source> sources = List.of(new Source("guide.md", "source", "fixture-sha"));
    private WikiStore store() { return new WikiStore(root, new ObjectMapper()); }
    private Draft draft() {
        return new Draft(List.of(
                new Page("index", "<목록>[안내]", "[[조건|상세 조건]] [[조건]] [[missing|없는 페이지]]", List.of("guide.md")),
                new Page("조건", "조건", "신청 조건 설명", List.of("guide.md"))));
    }
    @Test void navigationLinksPagesAndSourcesWithoutCollidingWithIndexPage() throws Exception {
        String id = store().saveDraft(draft(), sources);
        Path folder = root.resolve("drafts").resolve(id);
        String navigation = Files.readString(folder.resolve("_navigation/index.md"));
        assertThat(navigation).contains("[&lt;목록&gt;&#91;안내&#93;](../index.md)", "[조건](../조건.md)", "guide.md");
        assertThat(navigation).doesNotContain("../missing.md");
        assertThat(Files.readString(folder.resolve("index.md"))).contains("[[조건|상세 조건]]");
        String release = store().publish(id);
        assertThat(Files.readString(root.resolve("releases").resolve(release).resolve("_navigation/index.md"))).isEqualTo(navigation);
        assertThat(store().documents()).hasSize(2).allSatisfy(d -> assertThat(d.getText()).doesNotContain("# 위키 탐색"));
    }
    @Test void publicationRebuildsNavigationFromEditsAndPreservesEarlierRelease() throws Exception {
        WikiStore store = store();
        String id = store.saveDraft(draft(), sources);
        String first = store.publish(id);
        Path folder = root.resolve("drafts").resolve(id);
        Files.writeString(folder.resolve("index.md"), "사람이 참조를 제거함");
        Files.writeString(folder.resolve("조건.md"), "사람이 [[index|목록]] 참조를 추가함");
        Files.writeString(folder.resolve("_navigation/index.md"), "stale or edited catalogue");
        String second = store.publish(id);
        String updated = Files.readString(root.resolve("releases").resolve(second).resolve("_navigation/index.md"));
        assertThat(updated).doesNotContain("stale or edited");
        assertThat(updated).contains("참조하는 페이지: [&lt;목록&gt;&#91;안내&#93;](../index.md)");
        assertThat(Files.readString(root.resolve("releases").resolve(first).resolve("_navigation/index.md")))
                .contains("참조하는 페이지: [조건](../조건.md)");
    }
    @Test void oldDraftWithoutNavigationCanStillBePublished() throws Exception {
        String id = store().saveDraft(draft(), sources);
        Files.delete(root.resolve("drafts").resolve(id).resolve("_navigation/index.md"));
        String release = store().publish(id);
        assertThat(root.resolve("releases").resolve(release).resolve("_navigation/index.md")).isRegularFile();
    }
}
