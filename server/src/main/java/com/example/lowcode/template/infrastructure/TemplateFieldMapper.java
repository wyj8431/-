package com.example.lowcode.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TemplateFieldMapper {
    @Select("""
        SELECT field_key AS fieldKey, label, field_type AS fieldType,
               is_required AS required, default_value AS defaultValue
        FROM template_field
        WHERE template_id = #{templateId}
        ORDER BY id
        """)
    List<TemplateFieldRow> findByTemplateId(@Param("templateId") long templateId);

    class TemplateFieldRow {
        private String fieldKey;
        private String label;
        private String fieldType;
        private boolean required;
        private String defaultValue;

        public String getFieldKey() {
            return fieldKey;
        }

        public void setFieldKey(String fieldKey) {
            this.fieldKey = fieldKey;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getFieldType() {
            return fieldType;
        }

        public void setFieldType(String fieldType) {
            this.fieldType = fieldType;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }
    }
}
