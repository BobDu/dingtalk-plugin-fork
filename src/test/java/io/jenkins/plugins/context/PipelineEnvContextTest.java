package io.jenkins.plugins.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.EnvVars;
import org.junit.jupiter.api.Test;

class PipelineEnvContextTest {

  @Test
  void concurrentRunsAreIsolatedByBuild() {
    // 复现 #366:两个并发构建使用相同的变量名,彼此不应互相覆盖。
    PipelineEnvContext.mergeById("job/a#1", new EnvVars("FOO", "value-a"));
    PipelineEnvContext.mergeById("job/b#1", new EnvVars("FOO", "value-b"));

    assertEquals("value-a", PipelineEnvContext.getById("job/a#1").get("FOO"));
    assertEquals("value-b", PipelineEnvContext.getById("job/b#1").get("FOO"));

    PipelineEnvContext.resetById("job/a#1");
    PipelineEnvContext.resetById("job/b#1");
  }

  @Test
  void resetOnlyClearsTargetBuild() {
    PipelineEnvContext.mergeById("job/a#2", new EnvVars("K", "a"));
    PipelineEnvContext.mergeById("job/b#2", new EnvVars("K", "b"));

    PipelineEnvContext.resetById("job/a#2");

    assertTrue(PipelineEnvContext.getById("job/a#2").isEmpty());
    assertEquals("b", PipelineEnvContext.getById("job/b#2").get("K"));

    PipelineEnvContext.resetById("job/b#2");
  }

  @Test
  void mergeAccumulatesWithinSameBuild() {
    PipelineEnvContext.mergeById("job/a#3", new EnvVars("A", "1"));
    PipelineEnvContext.mergeById("job/a#3", new EnvVars("B", "2"));

    EnvVars merged = PipelineEnvContext.getById("job/a#3");
    assertEquals("1", merged.get("A"));
    assertEquals("2", merged.get("B"));

    PipelineEnvContext.resetById("job/a#3");
  }

  @Test
  void getReturnsDefensiveCopy() {
    PipelineEnvContext.mergeById("job/a#4", new EnvVars("K", "original"));

    PipelineEnvContext.getById("job/a#4").put("K", "mutated");

    assertEquals("original", PipelineEnvContext.getById("job/a#4").get("K"));

    PipelineEnvContext.resetById("job/a#4");
  }

  @Test
  void nullRunIsHandledGracefully() {
    PipelineEnvContext.merge(null, new EnvVars("X", "1"));
    assertTrue(PipelineEnvContext.get(null).isEmpty());
    PipelineEnvContext.reset(null);
  }

  @Test
  void mergeIgnoresNullValue() {
    PipelineEnvContext.mergeById("job/a#5", null);
    assertTrue(PipelineEnvContext.getById("job/a#5").isEmpty());
    PipelineEnvContext.resetById("job/a#5");
  }
}
