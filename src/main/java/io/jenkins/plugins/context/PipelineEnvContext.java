package io.jenkins.plugins.context;

import hudson.EnvVars;
import hudson.model.Run;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按构建(Run)隔离地缓存 pipeline 运行期收集到的环境变量。
 *
 * <p>历史实现以 {@link ThreadLocal} 存储,但 pipeline 的 step 运行在所有并发构建共享的 CPS
 * 线程池上,且线程会被跨构建复用,导致不同构建之间的环境变量相互覆盖(见 issue #366)。这里改为以
 * {@link Run#getExternalizableId()} 为键,按构建隔离存储,彻底消除线程复用带来的串号问题。
 */
public class PipelineEnvContext {

	private static final Map<String, EnvVars> STORE = new ConcurrentHashMap<>();

	private PipelineEnvContext() {
	}

	public static void merge(Run<?, ?> run, EnvVars value) {
		if (run != null) {
			mergeById(run.getExternalizableId(), value);
		}
	}

	public static EnvVars get(Run<?, ?> run) {
		return run == null ? new EnvVars() : getById(run.getExternalizableId());
	}

	public static void reset(Run<?, ?> run) {
		if (run != null) {
			resetById(run.getExternalizableId());
		}
	}

	static void mergeById(String id, EnvVars value) {
		if (id == null || value == null) {
			return;
		}
		STORE.compute(id, (key, current) -> {
			EnvVars merged = current == null ? new EnvVars() : current;
			merged.overrideAll(value);
			return merged;
		});
	}

	static EnvVars getById(String id) {
		EnvVars current = id == null ? null : STORE.get(id);
		return current == null ? new EnvVars() : new EnvVars(current);
	}

	static void resetById(String id) {
		if (id != null) {
			STORE.remove(id);
		}
	}
}
