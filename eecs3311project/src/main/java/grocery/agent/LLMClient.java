package grocery.agent;

public interface LLMClient {
    String ask(String prompt) throws Exception;

}
