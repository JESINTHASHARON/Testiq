package com.api.test.api_verifier.service;

import com.api.test.api_verifier.model.Conversation;
import com.api.test.api_verifier.model.Message;
import com.api.test.api_verifier.repository.ConversationRepository;
import com.api.test.api_verifier.repository.MessageRepository;
import com.api.test.api_verifier.service.ConversationService;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private TestSuiteService testSuiteService;

    @Autowired
    private TestExecutor testExecutor;

    @Autowired
    private AIService aiService;

    public List<Message> getMessages(String conversationId){
        return messageRepository.findByConversationId(conversationId);
    }
    public String sendMessage(String conversationId, String userInput) throws Exception {

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        Message userMsg = new Message();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userInput);
        messageRepository.save(userMsg);

        List<Message> history =
                messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversationId);

        Collections.reverse(history);

        if (history.size() == 1) {
            conversationService.updateTitleFromFirstMessage(conversationId, userInput);
        }

        Map<String, Object> suites = testSuiteService.getAllSuites();
        List<Map<String, Object>> metadata = testSuiteService.listAllTestcasesMetadata();

        GenerateContentResponse response =
                (GenerateContentResponse) aiService.generateWithHistory(history, suites, metadata);

        String finalReply = null;

        while (true) {

            List<FunctionCall> calls = response.functionCalls();

            if (calls == null || calls.isEmpty()) {

                String text = response.text();

                if (text == null || text.trim().isEmpty()) {
                    finalReply = "Operation completed successfully.";
                } else {
                    finalReply = text;
                }
                break;
            }

            for (FunctionCall call : calls) {

                Map<String, Object> args =
                        call.args().orElseThrow(
                                () -> new RuntimeException("Missing function arguments")
                        );

                String functionName =
                        call.name().orElseThrow(
                                () -> new RuntimeException("Missing function name")
                        );
                String toolResult = "";

                switch (functionName) {

                    case "create_test":
                        toolResult = testSuiteService.createTest(
                                (String) args.get("path"),
                                (Map<String, Object>) args.get("testcase")
                        );
                        break;

                    case "read_test":
                        toolResult = testSuiteService.readTest(
                                (String) args.get("path")
                        ).toString();
                        break;

                    case "update_test":
                        toolResult = testSuiteService.updateTest(
                                (String) args.get("path"),
                                (Map<String, Object>) args.get("testcase")
                        );
                        break;

                    case "rename_test":
                        toolResult = testSuiteService.renameTest(
                                (String) args.get("path"),
                                (String) args.get("name")
                        );
                        break;

                    case "delete_test":
                        toolResult = testSuiteService.deleteTest(
                                (String) args.get("path")
                        ).toString();
                        break;

                    case "create_suite":
                        toolResult = testSuiteService.createFolder(
                                (String) args.get("path")
                        ).toString();
                        break;

                    case "rename_suite":
                        toolResult = testSuiteService.renameFolder(
                                (String) args.get("path"),
                                (String) args.get("name")
                        );
                        break;

                    case "delete_suite":
                        toolResult = testSuiteService.deleteFolder(
                                (String) args.get("path")
                        ).toString();
                        break;

                    case "list_prechecks":
                        toolResult = testExecutor.getPrechecks().toString();
                        break;

                    case "add_precheck":
                        testExecutor.addPrecheck(args);
                        toolResult = "Precheck created successfully.";
                        break;

                    case "update_precheck":
                        testExecutor.updatePrecheck(
                                (String) args.get("name"),
                                args
                        );
                        toolResult = "Precheck updated successfully.";
                        break;

                    case "delete_precheck":
                        testExecutor.deletePrecheck(
                                (String) args.get("name")
                        );
                        toolResult = "Precheck deleted successfully.";
                        break;

                    default:
                        toolResult = "Unsupported function.";
                }
                Message functionMsg = new Message();
                functionMsg.setConversationId(conversationId);
                functionMsg.setRole("model");
                functionMsg.setContent("Function Call: " + functionName + " with args: " + args.toString());
                history.add(functionMsg);


                Message toolMsg = new Message();
                toolMsg.setConversationId(conversationId);
                toolMsg.setRole("model");
                toolMsg.setContent(toolResult);

                history.add(toolMsg);
            }

            response = (GenerateContentResponse) aiService.generateWithHistory(history, suites, metadata);
        }

        Message aiMsg = new Message();
        aiMsg.setConversationId(conversationId);
        aiMsg.setRole("model");
        aiMsg.setContent(finalReply);
        messageRepository.save(aiMsg);

        return finalReply;
    }
}