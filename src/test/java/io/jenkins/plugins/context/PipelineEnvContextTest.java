package io.jenkins.plugins.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import hudson.EnvVars;
import hudson.model.Run;
import org.junit.jupiter.api.Test;

class PipelineEnvContextTest {

  @Test
  void concurrentRunsAreIsolatedByBuild() {
    // 复现 #366:两个并发构建使用相同的变量名,彼此不应互相覆盖。
    Run<?, ?> a = mock(Run.class);
    Run<?, ?> b = mock(Run.class);

    PipelineEnvContext.merge(a, new EnvVars("FOO", "value-a"));
    PipelineEnvContext.merge(b, new EnvVars("FOO", "value-b"));

    assertEquals("value-a", PipelineEnvContext.get(a).get("FOO"));
    assertEquals("value-b", PipelineEnvContext.get(b).get("FOO"));

    PipelineEnvContext.reset(a);
    PipelineEnvContext.reset(b);
  }

  @Test
  void resetOnlyClearsTargetBuild() {
    Run<?, ?> a = mock(Run.class);
    Run<?, ?> b = mock(Run.class);
    PipelineEnvContext.merge(a, new EnvVars("K", "a"));
    PipelineEnvContext.merge(b, new EnvVars("K", "b"));

    PipelineEnvContext.reset(a);

    assertTrue(PipelineEnvContext.get(a).isEmpty());
    assertEquals("b", PipelineEnvContext.get(b).get("K"));

    PipelineEnvContext.reset(b);
  }

  @Test
  void mergeAccumulatesWithinSameBuild() {
    Run<?, ?> a = mock(Run.class);
    PipelineEnvContext.merge(a, new EnvVars("A", "1"));
    PipelineEnvContext.merge(a, new EnvVars("B", "2"));

    EnvVars merged = PipelineEnvContext.get(a);
    assertEquals("1", merged.get("A"));
    assertEquals("2", merged.get("B"));

    PipelineEnvContext.reset(a);
  }

  @Test
  void getReturnsDefensiveCopy() {
    Run<?, ?> a = mock(Run.class);
    PipelineEnvContext.merge(a, new EnvVars("K", "original"));

    PipelineEnvContext.get(a).put("K", "mutated");

    assertEquals("original", PipelineEnvContext.get(a).get("K"));

    PipelineEnvContext.reset(a);
  }

  @Test
  void mergeDoesNotMutateAlreadyReturnedSnapshot() {
    // EnvVars 继承自 TreeMap,不是线程安全的;已经交出去的快照不能被后续合并就地改写,
    // 否则并发读取时会拿到撕裂的数据。
    Run<?, ?> a = mock(Run.class);
    PipelineEnvContext.merge(a, new EnvVars("K", "v1"));
    EnvVars snapshot = PipelineEnvContext.get(a);

    PipelineEnvContext.merge(a, new EnvVars("K", "v2"));

    assertEquals("v1", snapshot.get("K"));
    assertEquals("v2", PipelineEnvContext.get(a).get("K"));

    PipelineEnvContext.reset(a);
  }

  @Test
  void entriesAreKeyedByBuildIdentityNotByName() {
    // 键按引用比较,因此两个构建即便对外标识相同也不会串号,
    // 任务改名或移动也不会让已缓存的环境变量失联。
    Run<?, ?> a = mock(Run.class);
    Run<?, ?> b = mock(Run.class);

    PipelineEnvContext.merge(a, new EnvVars("K", "a"));

    assertTrue(PipelineEnvContext.get(b).isEmpty());
    assertEquals("a", PipelineEnvContext.get(a).get("K"));

    PipelineEnvContext.reset(a);
  }

  @Test
  void nullRunIsHandledGracefully() {
    PipelineEnvContext.merge(null, new EnvVars("X", "1"));
    assertTrue(PipelineEnvContext.get(null).isEmpty());
    PipelineEnvContext.reset(null);
  }

  @Test
  void mergeIgnoresNullValue() {
    Run<?, ?> a = mock(Run.class);
    PipelineEnvContext.merge(a, null);
    assertTrue(PipelineEnvContext.get(a).isEmpty());
    PipelineEnvContext.reset(a);
  }
}
