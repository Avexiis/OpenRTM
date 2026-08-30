package openrtm.games;

import openrtm.console.ConsoleService;

@FunctionalInterface
public interface StatsApplier {
    void apply(ConsoleService service, ActionContext context) throws Exception;
}
