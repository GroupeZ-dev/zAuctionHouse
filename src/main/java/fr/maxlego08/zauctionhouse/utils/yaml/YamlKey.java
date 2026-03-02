package fr.maxlego08.zauctionhouse.utils.yaml;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a YAML key with its associated comments and value.
 */
public class YamlKey {

    private final String fullPath;
    private final String key;
    private final int indentLevel;
    private final List<String> precedingComments;
    private final String inlineComment;
    private String rawLine;
    private Object value;
    private boolean isList;

    public YamlKey(String fullPath, String key, int indentLevel) {
        this.fullPath = fullPath;
        this.key = key;
        this.indentLevel = indentLevel;
        this.precedingComments = new ArrayList<>();
        this.inlineComment = null;
        this.isList = false;
    }

    public YamlKey(String fullPath, String key, int indentLevel, String inlineComment) {
        this.fullPath = fullPath;
        this.key = key;
        this.indentLevel = indentLevel;
        this.precedingComments = new ArrayList<>();
        this.inlineComment = inlineComment;
        this.isList = false;
    }

    public String getFullPath() {
        return fullPath;
    }

    public String getKey() {
        return key;
    }

    public int getIndentLevel() {
        return indentLevel;
    }

    public List<String> getPrecedingComments() {
        return precedingComments;
    }

    public void addPrecedingComment(String comment) {
        this.precedingComments.add(comment);
    }

    public String getInlineComment() {
        return inlineComment;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean isList() {
        return isList;
    }

    public void setList(boolean list) {
        isList = list;
    }

    public boolean isSection() {
        return value == null && !isList;
    }

    @Override
    public String toString() {
        return "YamlKey{fullPath='" + fullPath + "', key='" + key + "', indent=" + indentLevel + "}";
    }
}
