package com.api.test.api_verifier.service;

import com.api.test.api_verifier.model.Message;
import com.google.genai.Client;
import com.google.genai.types.*;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AIService {

    private final Client client = new Client();
    public GenerateContentResponse generateWithHistory(List<Message> history, Map<String, Object> suites
            , List<Map<String, Object>> metadata) {
        try {
            List<Content> contents = new ArrayList<>();
            String model = System.getenv("MODEL");
            contents.add(
                    Content.fromParts(
                            Part.fromText("""
                                    You are TestIQ Internal Engine.
                                    
                                    Your responsibility:
                                    Generate or modify TestIQ testcases strictly according to TestIQ rules.
                                    Clear doubts regarding testiq
                                    Answer the questions about testiq, automation
                                    Dont answer everything in JSON, if the question is answerable in text, answer it in text alone, not in JSON format
                                    
                                    CONTEXT USAGE RULE
                                    
                                    Use previous conversation context ONLY if it is required to correctly answer the current user request.
                                    
                                    If the current request is independent and complete on its own, ignore earlier unrelated messages.
                                    
                                    Do NOT merge unrelated informational responses into testcase output.
                                    
                                    Focus primarily on the most recent user instruction.
                                    
                                    DONT REPLY THINGS OTHER THAN TESTIQ- it is very very important
                                    --------------------------------------------------
                                    CORE BEHAVIOR
                                    --------------------------------------------------
                                    
                                    1. You generate or modify ONE TestIQ testcase at a time if asked and endpoint and API response is provided.
                                    2. Output must be ONLY one valid JSON object, other than that you can add some extra text for users.
                                    3. No markdown.
                                    4. No commentary.
                                    
                                    If required information is missing, ask a clarification question instead of guessing.
                                    
                                    --------------------------------------------------
                                    STRICT TESTCASE STRUCTURE
                                    --------------------------------------------------
                                    
                                    {
                                      "name": "string",
                                      "endpoint": "relative path only (no baseUrl, no domain)",
                                      "method": "GET | POST | PUT | DELETE",
                                      "suite": "string (must match user-provided suite)",
                                      "path": "string", (This path differs from checks-> path, in this testcase path you must mention, under which suites it will be created, for example user asks you to create under Orders suite, and Orders suite is under Mock Suite, then the path must be Mock/Orders.)
                                      "expectedStatus": number,
                                      "parentId": number (null by default if not given),
                                      "preCheck": String (null by default, it must include name of precheck, if given),
                                      "checks": [
                                        {
                                          "type": "fieldExistence | valueMatch | patternMatch | keyPresence",
                                          "path": "valid JSONPath",
                                          "fields": ["array of required fields"],
                                          "fieldAny": ["array where at least one must exist"],
                                          "operator": "== | != | > | < | >= | <=",
                                          "expected": "value"
                                        }
                                        requires: [] (Array)
                                        
                                        
                                      ]
                                    }
                                    
                                    Do NOT add any additional properties.
                                    
                                    --------------------------------------------------
                                    ENDPOINT RULE (CRITICAL)
                                    --------------------------------------------------
                                    
                                    - Endpoint must be relative path only.
                                    - Never include protocol.
                                    - Never include domain.
                                    - Never include baseUrl.
                                    - Example: "/emsapi/adminhomepage"
                                    - Invalid: "https://example.com/emsapi/adminhomepage"
                                    
                                    If endpoint is unclear, ask user.
                                    
                                    --------------------------------------------------------------------------------
                                    CRITICAL PATH RULE
                                    
                                    There are THREE different paths:
                                    
                                    1. Function argument "path" → full file path including .json
                                       Example: Mock/Orders/Test1.json
                                    
                                    2. Testcase property "path" → suite folder path only
                                       Example: Mock/Orders
                                    
                                    3. checks[].path → JSONPath like "$", "$[*].items[*]"
                                    
                                    Never mix them.
                                    Never use "$" for testcase.path.
                                    Never use suite name alone.
                                    --------------------------------------------------
                                    SUITE RULE
                                    --------------------------------------------------
                                    
                                    - Use suite exactly as provided by user.
                                    - If suite not provided, ask user.
                                    - Never invent suite names.
                                    
                                    --------------------------------------------------
                                    CHECK TYPE RULES
                                    --------------------------------------------------
                                    
                                    Allowed check types only:
                                    - fieldExistence
                                    - valueMatch
                                    - patternMatch
                                    - keyPresence
                                    
                                    Never invent new check types.
                                    
                                    --------------------------------------------------
                                    fieldExistence RULE
                                    --------------------------------------------------
                                    
                                    - "fields" → ALL listed must exist in the path.
                                    - "fieldAny" → AT LEAST ONE values provided must exist in the path.
                                    - Do not confuse them.
                                    
                                    Example:
                                    {
                                      "type": "fieldExistence",
                                      "path": "$[*].pages[*]",
                                      "fields": ["name","displayName"],
                                      "fieldAny": ["route","url"]
                                    }
                                    
                                    Meaning:
                                    "name" and "displayName" must exist.
                                    Either "route" OR "url" must exist.
                                    
                                    --------------------------------------------------
                                    valueMatch RULE
                                    --------------------------------------------------
                                    
                                    Use only for deterministic values.
                                    
                                    Do NOT validate:
                                    - id
                                    - timestamp
                                    - token
                                    - dynamic values (including numbers)
                                    
                                    --------------------------------------------------
                                    JSONPATH RULES
                                    --------------------------------------------------
                                    
                                    Root object → $
                                    Root array → $[*]
                                    Nested array → array[*]
                                    Filtering → array[key=value]
                                    Multi-level filtering allowed.
                                    
                                    Do not invent unsupported JSONPath syntax.
                                    
                                    --------------------------------------------------
                                    keyPresence RULE
                                    --------------------------------------------------
                                    
                                    Use for validating existence of specific object in array using filter.
                                    
                                    Example:
                                    $[*][name=globalSettings].pages[displayName=DEX Manager]
                                    
                                    --------------------------------------------------
                                    PARENT-CHILD RULE
                                    --------------------------------------------------
                                    
                                    If user requests child testcase:
                                    
                                    Ask:
                                    "There are existing testcases above this.
                                    Do you want to configure any as parent?"
                                    
                                    Do NOT auto assign parent.
                                    Do NOT guess dependencies.
                                    Add only when user provided, otherwise leave it
                                    
                                    --------------------------------------------------
                                    MODIFICATION RULE
                                    --------------------------------------------------
                                    
                                    If user asks to modify existing testcase:
                                    
                                    - Preserve structure.
                                    - Only update requested fields.
                                    - Do not regenerate entire testcase unless explicitly asked.
                                    
                                    --------------------------------------------------
                                    WHEN INFORMATION IS MISSING
                                    --------------------------------------------------
                                    
                                    If any of these are missing:
                                    - suite
                                    - endpoint
                                    - method
                                    - expectedStatus
                                    
                                    Ask clarification instead of guessing.
                                    --------------------------------------------------
                                    FUNCTION CALLING RULE
                                    --------------------------------------------------
                                    DONT CREATE NEW TESTCASE, IF USER ASKS FOR UPDATION
                                    
                                    If the user requests to create a testcase:
                                    
                                    - You MUST call the function "create_test".
                                    - Do NOT output raw JSON.
                                    - Do NOT explain.
                                    - Do NOT return only the testcase object.
                                    - You MUST supply BOTH:
                                      1. "path" → full file system path like "Mock/Orders/TestName.json"
                                      2. "testcase" → the structured testcase object.
                                      
                                    if User requests to update a testcase
                                    
                                    -You must call the function "update_test".
                                    -Do not output the raw JSON
                                    -You MUST supply BOTH:
                                      1. "path" → full file system path like "Mock/Orders/TestName.json"
                                      2. "testcase" → the structured testcase object.
                                    
                                    If the user requests to read testcases
                                    
                                    - You must call the function "read_test".
                                    - You MUST supply "path" → full file system path like "Mock/Orders/TestName.json"
                                    - send the response as message 
                                    
                                    
                                    If the user requests to delete testcases 
                                    
                                    - You must call the function "delete_test".
                                    - You MUST supply "path" → full file system path like "Mock/Orders/TestName.json"
                                    - If user provides testcase id, you may call "read_test" function with paths provided from structure, you no need to tell the
                                      users that you read testcases, but after find out the testcase id, you may proceed with "delete_test" function with the correct path.
                                    
                                    Determine correct path using the provided suite structure.
                                    Use the metadata if required to match testcase id.
                                    If user says "inside Orders", and Orders is inside Mock, then:
                                    path = "Mock/Orders/<TestName>.json"
                                    
                                    If the user ask you to create testcase under some suite, and that testsuite does not exists, tell the user "No such Testsuite found",
                                    instead of creating the testcase at random places
                                    
                                    Never use "$" as file path.
                                    Never use suite name as file path.
                                    
                                    If user requests to create precheck:
                                    - Call "add_precheck"
                                    - Do not output raw JSON
                                    
                                    If user requests to update precheck:
                                    - Call "update_precheck"
                                    
                                    If user requests to delete precheck:
                                    - Call "delete_precheck"
                                    
                                    If user requests to list prechecks:
                                    - Call "list_prechecks"
                                    --------------------------------------------------
                                    When generating a testcase:
                                    - Use provided suite structure to determine correct path.
                                    - The path must match an existing folder unless user explicitly requests new folder creation.
                                    - for example if they ask inside orders, and in folder structure the orders is in Mock folder, the new testcase name is tc1
                                      then the path will be "Mock/orders/tc1.json"
                                    
                                    You behave as strict internal helper engine of TestIQ.
                                    """)
                    )
            );

            contents.add(
                    Content.fromParts(
                            Part.fromText(
                                    "Available TestIQ Suite Structure:\n" +
                                            suites.toString() +
                                            "\n\nUse this structure to determine correct folder path when creating or modifying tests. " +
                                            "Do NOT invent new folders unless user explicitly requests.+\n"+metadata.toString()+"\n\nUse this to map testcase with its id "
                            )
                    )
            );
            for (Message msg : history) {
                contents.add(
                        Content.builder()
                                .role(msg.getRole())
                                .parts(List.of(
                                        Part.fromText(msg.getContent())
                                ))
                                .build()
                );
            }


            FunctionDeclaration readTest =
                    FunctionDeclaration.builder()
                            .name("read_test")
                            .description("Read TestIQ Testcases")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of(
                                                    "path", Schema.builder()
                                                            .type("string")
                                                            .description("Full file path like Mock/Orders/Test1.json")
                                                            .build()
                                            )).required(List.of("path"))
                                            .build()
                            ).build();

            FunctionDeclaration renameTest =
                    FunctionDeclaration.builder()
                            .name("rename_test")
                            .description("Rename a TestIQ Testcase")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of(
                                                    "path", Schema.builder()
                                                            .type("string")
                                                            .description("Full file path like Mock/Orders/Test1.json")
                                                            .build(),
                                                    "name", Schema.builder()
                                                            .type("string")
                                                            .description("New name")
                                                            .build()
                                            )).required(List.of("path", "name"))
                                            .build()
                            ).build();

            FunctionDeclaration deleteTest =
                    FunctionDeclaration.builder()
                            .name("delete_test")
                            .description("Delete a TestIQ Testcases")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of(
                                                    "path", Schema.builder()
                                                            .type("string")
                                                            .description("Full file path like Mock/Orders/Test1.json")
                                                            .build()
                                            ))
                                            .required(List.of("path"))
                                            .build()
                            ).build();

            
            FunctionDeclaration listTests =
                    FunctionDeclaration.builder()
                            .name("list_testcases")
                            .description("Return metadata of all testcases including id, name and path")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of())
                                            .build()
                            )
                            .build();

            FunctionDeclaration createTest =
                    FunctionDeclaration.builder()
                            .name("create_test")
                            .description("Create a TestIQ testcase")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of(
                                                    "path", Schema.builder()
                                                            .type("string")
                                                            .description("Full file path like Mock/Orders/Test1.json")
                                                            .build(),
                                                    "testcase", Schema.builder()
                                                            .type("object")
                                                            .properties(Map.of(
                                                                    "name", Schema.builder().type("string").build(),
                                                                    "endpoint", Schema.builder().type("string").build(),
                                                                    "method", Schema.builder().type("string").build(),
                                                                    "suite", Schema.builder().type("string").build(),
                                                                    "path", Schema.builder()
                                                                            .type("string")
                                                                            .description("Suite folder path like Mock/Orders")
                                                                            .build(),
                                                                    "expectedStatus", Schema.builder().type("number").build(),
                                                                    "parentId", Schema.builder()
                                                                            .type("number")
                                                                            .nullable(true)
                                                                            .build(),
                                                                    "precheck", Schema.builder()
                                                                            .type("string")
                                                                            .nullable(true)
                                                                            .build(),
                                                                    "requires", Schema.builder()
                                                                            .type("array")
                                                                            .items(Schema.builder().type("string").build())
                                                                            .build(),
                                                                    "checks", Schema.builder()
                                                                            .type("array")
                                                                            .items(
                                                                                    Schema.builder()
                                                                                            .type("object")
                                                                                            .properties(Map.of(
                                                                                                    "type", Schema.builder().type("string").build(),
                                                                                                    "path", Schema.builder().type("string").build(),
                                                                                                    "fields", Schema.builder()
                                                                                                            .type("array")
                                                                                                            .items(Schema.builder().type("string").build())
                                                                                                            .build(),
                                                                                                    "fieldAny", Schema.builder()
                                                                                                            .type("array")
                                                                                                            .items(Schema.builder().type("string").build())
                                                                                                            .build(),
                                                                                                    "operator", Schema.builder().type("string").build(),
                                                                                                    "expected", Schema.builder().type("string").build()
                                                                                            ))
                                                                                            .required(List.of("type", "path"))
                                                                                            .build()
                                                                            )
                                                                            .build()
                                                            ))
                                                            .required(List.of(
                                                                    "name",
                                                                    "endpoint",
                                                                    "method",
                                                                    "suite",
                                                                    "path",
                                                                    "expectedStatus"
                                                            ))
                                                            .build()
                                            ))
                                            .required(List.of("path", "testcase"))
                                            .build()
                            )
                            .build();

            FunctionDeclaration updateTest =
                    FunctionDeclaration.builder()
                            .name("update_test")
                            .description("Update a TestIQ testcase")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of(
                                                    "path", Schema.builder()
                                                            .type("string")
                                                            .description("Full file path like Mock/Orders/Test1.json")
                                                            .build(),
                                                    "testcase", Schema.builder()
                                                            .type("object")
                                                            .properties(Map.of(
                                                                    "name", Schema.builder().type("string").build(),
                                                                    "endpoint", Schema.builder().type("string").build(),
                                                                    "method", Schema.builder().type("string").build(),
                                                                    "suite", Schema.builder().type("string").build(),
                                                                    "path", Schema.builder()
                                                                            .type("string")
                                                                            .description("Suite folder path like Mock/Orders")
                                                                            .build(),
                                                                    "expectedStatus", Schema.builder().type("number").build(),
                                                                    "parentId", Schema.builder()
                                                                            .type("number")
                                                                            .nullable(true)
                                                                            .build(),
                                                                    "precheck", Schema.builder()
                                                                            .type("string")
                                                                            .nullable(true)
                                                                            .build(),
                                                                    "requires", Schema.builder()
                                                                            .type("array")
                                                                            .items(Schema.builder().type("string").build())
                                                                            .build(),
                                                                    "checks", Schema.builder()
                                                                            .type("array")
                                                                            .items(
                                                                                    Schema.builder()
                                                                                            .type("object")
                                                                                            .properties(Map.of(
                                                                                                    "type", Schema.builder().type("string").build(),
                                                                                                    "path", Schema.builder().type("string").build(),
                                                                                                    "fields", Schema.builder()
                                                                                                            .type("array")
                                                                                                            .items(Schema.builder().type("string").build())
                                                                                                            .build(),
                                                                                                    "fieldAny", Schema.builder()
                                                                                                            .type("array")
                                                                                                            .items(Schema.builder().type("string").build())
                                                                                                            .build(),
                                                                                                    "operator", Schema.builder().type("string").build(),
                                                                                                    "expected", Schema.builder().type("string").build()
                                                                                            ))
                                                                                            .required(List.of("type", "path"))
                                                                                            .build()
                                                                            )
                                                                            .build()
                                                            ))
                                                            .required(List.of(
                                                                    "name",
                                                                    "endpoint",
                                                                    "method",
                                                                    "suite",
                                                                    "path",
                                                                    "expectedStatus"
                                                            ))
                                                            .build()
                                            ))
                                            .required(List.of("path", "testcase"))
                                            .build()
                            )
                            .build();

            FunctionDeclaration deleteSuite =
                    FunctionDeclaration.builder()
                            .name("delete_suite")
                            .description("Delete a TestIQ Test-Suite")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of(
                                                    "path", Schema.builder()
                                                            .type("string")
                                                            .description("Full file path like Mock/Orders/Test1.json")
                                                            .build()
                                            )).required(List.of("path"))
                                            .build()
                            ).build();


            FunctionDeclaration renameSuite =
                    FunctionDeclaration.builder()
                            .name("rename_suite")
                            .description("Rename a TestIQ Test-Suite")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of(
                                                    "path", Schema.builder()
                                                            .type("string")
                                                            .description("Full file path like Mock/Orders/Test1.json")
                                                            .build(),
                                                    "name", Schema.builder()
                                                            .type("string")
                                                            .description("New name")
                                                            .build()
                                            )).required(List.of("path", "name"))
                                            .build()
                            ).build();


            FunctionDeclaration createSuite =
                    FunctionDeclaration.builder()
                            .name("create_suite")
                            .description("Create a TestIQ Test-Suite")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of(
                                                    "path", Schema.builder()
                                                            .type("string")
                                                            .description("Full file path like Mock/Orders/Test1.json")
                                                            .build()
                                            )).required(List.of("path"))
                                            .build()
                            ).build();


            FunctionDeclaration listPrechecks =
                    FunctionDeclaration.builder()
                            .name("list_prechecks")
                            .description("List all available prechecks")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of())
                                            .build()
                            )
                            .build();

            FunctionDeclaration addPrecheck =
                    FunctionDeclaration.builder()
                            .name("add_precheck")
                            .description("Create a new precheck")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of(
                                                    "name", Schema.builder().type("string").build(),
                                                    "rule", Schema.builder()
                                                            .type("object")
                                                            .properties(Map.of(
                                                                    "endpoint", Schema.builder().type("string").build(),
                                                                    "method", Schema.builder().type("string").build(),
                                                                    "extractPath", Schema.builder().type("string").build(),
                                                                    "operator", Schema.builder().type("string").build(),
                                                                    "value", Schema.builder().type("string").build()
                                                            ))
                                                            .required(List.of(
                                                                    "endpoint",
                                                                    "method",
                                                                    "extractPath",
                                                                    "operator",
                                                                    "value"
                                                            ))
                                                            .build()
                                            ))
                                            .required(List.of("name", "rule"))
                                            .build()
                            )
                            .build();


            FunctionDeclaration updatePrecheck =
                    FunctionDeclaration.builder()
                            .name("update_precheck")
                            .description("Update an existing precheck")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of(
                                                    "name", Schema.builder().type("string").build(),
                                                    "rule", Schema.builder()
                                                            .type("object")
                                                            .properties(Map.of(
                                                                    "endpoint", Schema.builder().type("string").build(),
                                                                    "method", Schema.builder().type("string").build(),
                                                                    "extractPath", Schema.builder().type("string").build(),
                                                                    "operator", Schema.builder().type("string").build(),
                                                                    "value", Schema.builder().type("string").build()
                                                            ))
                                                            .required(List.of(
                                                                    "endpoint",
                                                                    "method",
                                                                    "extractPath",
                                                                    "operator",
                                                                    "value"
                                                            ))
                                                            .build()
                                            ))
                                            .required(List.of("name", "rule"))
                                            .build()
                            )
                            .build();


            FunctionDeclaration deletePrecheck =
                    FunctionDeclaration.builder()
                            .name("delete_precheck")
                            .description("Delete a precheck by name")
                            .parameters(
                                    Schema.builder()
                                            .type("object")
                                            .properties(Map.of(
                                                    "name", Schema.builder().type("string").build()
                                            ))
                                            .required(List.of("name"))
                                            .build()
                            )
                            .build();

            Tool tool = Tool.builder()
                    .functionDeclarations(List.of(
                            createTest,
                            readTest,
                            updateTest,
                            renameTest,
                            deleteTest,
                            listTests,
                            createSuite,
                            renameSuite,
                            deleteSuite,
                            updatePrecheck,
                            addPrecheck,
                            listPrechecks,
                            deletePrecheck
                    )).build();


            GenerateContentConfig config =
                    GenerateContentConfig.builder()
                            .tools(List.of(tool))
                            .temperature(0.1f)
                            .build();

            GenerateContentResponse response =
                    client.models.generateContent(
                            model,
                            contents,
                            config
                    );

            return response;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("AI service temporarily unavailable.", e);
        }
    }
}