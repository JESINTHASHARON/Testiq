package com.api.test.api_verifier.validator;

public class ValidatorFactory {

    public static Validator getValidator(String type) {
        if (type == null)
            return null;

        switch (type) {
            case "keyPresence":
                return new KeyPresenceValidator();
            case "patternMatch":
                return new PatternMatchValidator();
            case "fieldExistence":
                return new FieldExistenceValidator();
            case "valueMatch":
                return new ValueMatchValidator();
            default:
                return null;
        }
    }
}
