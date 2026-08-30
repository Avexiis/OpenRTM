package openrtm.games;

@FunctionalInterface
public interface ActionRunner {
    void run(ActionContext context) throws Exception;
}
