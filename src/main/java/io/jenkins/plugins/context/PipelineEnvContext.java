package io.jenkins.plugins.context;

import com.github.benmanes.caffeine.cache.Caffeine;
import hudson.EnvVars;
import hudson.model.Run;
import java.util.Map;

/**
 * 按构建(Run)隔离地缓存 pipeline 运行期收集到的环境变量。
 *
 * <p>历史实现以 {@link ThreadLocal} 存储,但 pipeline 的 step 运行在所有并发构建共享的 CPS
 * 线程池上,且线程会被跨构建复用,导致不同构建之间的环境变量相互覆盖(见 issue #366)。
 *
 * <p>这里以构建对象本身为键。键采用弱引用,因此即使某个构建没有走到清理逻辑,其缓存也会在构建对象
 * 被回收时一并释放;同时弱引用键按引用(而非 {@code equals})比较,任务改名或移动都不会让缓存失联。
 */
public class PipelineEnvContext {

	private static final Map<Run<?, ?>, EnvVars> store =
			Caffeine.newBuilder().weakKeys().<Run<?, ?>, EnvVars>build().asMap();

	public static void merge(Run<?, ?> run, EnvVars value) {
		if (run == null || value == null) {
			return;
		}
		// EnvVars 继承自 TreeMap,并非线程安全,因此每次合并都产生新的快照:
		// 存入的实例此后不再被修改,读取方无需加锁也不会看到写到一半的数据。
		store.compute(run, (key, current) -> {
			EnvVars merged = current == null ? new EnvVars() : new EnvVars(current);
			merged.overrideAll(value);
			return merged;
		});
	}

	public static EnvVars get(Run<?, ?> run) {
		EnvVars current = run == null ? null : store.get(run);
		return current == null ? new EnvVars() : new EnvVars(current);
	}

	public static void reset(Run<?, ?> run) {
		if (run != null) {
			store.remove(run);
		}
	}
}
