package edu.macalester.wpsemsim.lucene;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.FieldInfo;

/**
 * Based closely on StringField, but stores norms.
 */
public class NormedStringField extends Field {

    /** Indexed, not tokenized, omits norms, indexes
     *  DOCS_ONLY, not stored. */
    public static final FieldType TYPE_NOT_STORED = new FieldType();

    /** Indexed, not tokenized, omits norms, indexes
     *  DOCS_ONLY, stored */
    public static final FieldType TYPE_STORED = new FieldType();

    static {
        TYPE_NOT_STORED.setIndexed(true);
        TYPE_NOT_STORED.setOmitNorms(false);
        TYPE_NOT_STORED.setIndexOptions(FieldInfo.IndexOptions.DOCS_AND_FREQS);
        TYPE_NOT_STORED.setTokenized(false);
        TYPE_NOT_STORED.freeze();

        TYPE_STORED.setIndexed(true);
        TYPE_STORED.setOmitNorms(false);
        TYPE_STORED.setIndexOptions(FieldInfo.IndexOptions.DOCS_AND_FREQS);
        TYPE_STORED.setStored(true);
        TYPE_STORED.setTokenized(false);
        TYPE_STORED.freeze();
    }

    /** Creates a new NormedStringField.
     *  @param name field name
     *  @param value String value
     *  @param stored Store.YES if the content should also be stored
     *  @throws IllegalArgumentException if the field name or value is null.
     */
    public NormedStringField(String name, String value, Field.Store stored) {
        super(name, value, stored == Field.Store.YES ? TYPE_STORED : TYPE_NOT_STORED);
    }
}
