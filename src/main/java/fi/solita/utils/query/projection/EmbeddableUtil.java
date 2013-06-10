package fi.solita.utils.query.projection;

import static fi.solita.utils.functional.Collections.newList;
import static fi.solita.utils.functional.Functional.head;
import static fi.solita.utils.functional.Functional.last;
import static fi.solita.utils.functional.Functional.map;
import static fi.solita.utils.functional.Functional.sort;
import static fi.solita.utils.functional.Functional.zip;
import static fi.solita.utils.functional.Option.None;
import static fi.solita.utils.functional.Option.Some;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.Selection;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Attribute.PersistentAttributeType;
import javax.persistence.metamodel.Bindable;
import javax.persistence.metamodel.EmbeddableType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.Type.PersistenceType;

import fi.solita.utils.functional.Option;
import fi.solita.utils.functional.Tuple2;
import fi.solita.utils.query.QueryUtils_;
import fi.solita.utils.query.attributes.JoiningAttribute;
import fi.solita.utils.query.attributes.PseudoAttribute;
import fi.solita.utils.query.attributes.RelationAttribute;

public class EmbeddableUtil {

    static Iterable<? extends Selection<?>> breakEmbeddableToParts(Metamodel metamodel, Bindable<?> target, final Join<?,Object> source) {
        return map(getEmbeddableAttributes(target, metamodel), QueryUtils_.get.ap(source));
    }
    
    static Object instantiate(Class<?> clazz) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    static Object collectEmbeddableFromParts(Metamodel metamodel, Bindable<?> attr, Iterable<Object> columns) {
        List<Object> cols = newList(columns);
        List<? extends Attribute<?, ?>> embeddableAttributes = getEmbeddableAttributes(attr, metamodel);
        if (embeddableAttributes.size() != cols.size()) {
            throw new IllegalStateException("Expected same size for: " + embeddableAttributes + ", " + cols);
        }
        Class<?> embeddableClass = getEmbeddableType(attr, metamodel).getJavaType();
        try {
            Object embeddable = instantiate(embeddableClass);
            for (Tuple2<? extends Attribute<?, ?>, Object> a: zip(embeddableAttributes, cols)) {
                Member member = a._1.getJavaMember();
                if (member instanceof Field) {
                    Field f = (Field)member;
                    f.setAccessible(true);
                    f.set(embeddable, a._2);
                } else {
                    Method m = (Method)member;
                    if (m.getParameterTypes().length == 1 && head(m.getParameterTypes()).isAssignableFrom(a._1.getJavaType())) {
                        m.setAccessible(true);
                        m.invoke(embeddable, a._2);
                    } else {
                        throw new UnsupportedOperationException("not implemented. Run, Forrest, run!");
                    }
                }
            }
            return embeddable;
        } catch (Exception e)  {
            throw new RuntimeException(e);
        }
    }
    
    static Option<? extends Attribute<?,?>> unwrapEmbeddableAttribute(Attribute<?,?> attribute) {
        if (attribute == null || attribute instanceof PseudoAttribute) {
            return None();
        }
        if (attribute instanceof JoiningAttribute && last(((JoiningAttribute) attribute).getAttributes()) instanceof PseudoAttribute) {
            return None();
        }
        
        if (attribute.getPersistentAttributeType() == PersistentAttributeType.EMBEDDED) {
            return Some(attribute);
        }
        if (attribute instanceof RelationAttribute) {
            List<Attribute<?, ?>> params = ((RelationAttribute)attribute).getConstructor().getParameters();
            if (params.size() == 1) {
                return unwrapEmbeddableAttribute(head(params));
            }
        }
        return None();
    }

    static boolean isCollectionOfEmbeddables(Attribute<?, ?> attribute) {
        return attribute.getPersistentAttributeType() == PersistentAttributeType.ELEMENT_COLLECTION &&
               attribute instanceof PluralAttribute &&
               ((PluralAttribute<?,?,?>)attribute).getElementType().getPersistenceType() == PersistenceType.EMBEDDABLE;
    }

    static <T> EmbeddableType<T> getEmbeddableType(Bindable<T> attribute, Metamodel metamodel) {
        return metamodel.embeddable(attribute.getBindableJavaType());
    }
    
    static List<? extends Attribute<?,?>> getEmbeddableAttributes(Bindable<?> attribute, Metamodel metamodel) {
        return newList(sort(EmbeddableUtil.getEmbeddableType(attribute, metamodel).getAttributes(), attributeByName));
    }
    
    static final Comparator<Attribute<?,?>> attributeByName = new Comparator<Attribute<?,?>>() {
        @Override
        public int compare(Attribute<?, ?> o1, Attribute<?, ?> o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };
}
