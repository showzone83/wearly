package app.wearly.wardrobe;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidSubTypeValidator implements ConstraintValidator<ValidSubType, ItemInput> {

    @Override
    public boolean isValid(ItemInput input, ConstraintValidatorContext context) {
        if (input == null || input.category() == null || input.subType() == null) {
            return true; // 빈 값은 @NotNull이 잡는다
        }
        if (input.subType().belongsTo(input.category())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("subType")
                .addConstraintViolation();
        return false;
    }
}
