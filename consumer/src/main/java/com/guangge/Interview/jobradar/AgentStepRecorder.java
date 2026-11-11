package com.guangge.Interview.jobradar;

import java.util.ArrayList;
import java.util.List;

public final class AgentStepRecorder {
    private final List<AgentStep> steps = new ArrayList<>();

    public <T> T measure(String tool, ThrowingSupplier<T> work) throws Exception {
        long started = System.currentTimeMillis();
        try {
            T result = work.get();
            steps.add(new AgentStep(tool, System.currentTimeMillis() - started, true, null));
            return result;
        } catch (Exception exception) {
            steps.add(new AgentStep(tool, System.currentTimeMillis() - started, false,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
            throw exception;
        }
    }

    public void record(String tool, long ms, boolean ok, String note) {
        steps.add(new AgentStep(tool, ms, ok, note));
    }

    public List<AgentStep> steps() {
        return List.copyOf(steps);
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
