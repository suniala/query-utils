package fi.solita.utils.query.projection;

import static fi.solita.utils.functional.Collections.newArray;
import static fi.solita.utils.functional.Collections.newSet;
import static fi.solita.utils.functional.Functional.head;
import static fi.solita.utils.functional.Functional.map;
import static fi.solita.utils.functional.Functional.range;
import static fi.solita.utils.functional.Functional.size;
import static fi.solita.utils.functional.Functional.zip;
import static fi.solita.utils.query.QueryUtils.isRequiredByMetamodel;
import static fi.solita.utils.query.QueryUtils.isRequiredByQueryAttribute;
import static fi.solita.utils.query.attributes.AttributeProxy.unwrap;
import static fi.solita.utils.query.projection.ProjectionResultUtil_.convertNullsToEmbeddableWhereRequired;
import static fi.solita.utils.query.projection.ProjectionResultUtil_.postProcessValue;
import static fi.solita.utils.query.projection.ProjectionResultUtil_.transformPseudoResultToActualValue;
import static fi.solita.utils.query.projection.ProjectionResultUtil_.wrapNullsToOptionsWhereAppropriate;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.solita.utils.functional.Option;
import fi.solita.utils.functional.Tuple;
import fi.solita.utils.functional.Tuple2;
import fi.solita.utils.query.attributes.PseudoAttribute;
import fi.solita.utils.query.codegen.MetaJpaConstructor;

class ProjectionResultUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectionResultUtil.class);
    
    public static final class NullValueButNonOptionConstructorArgumentException extends RuntimeException {
        private final Class<?> argumentType;
        private final int argumentIndex;
        private final Class<?> constructor;
    
        public NullValueButNonOptionConstructorArgumentException(Class<?> constructor, Class<?> argumentType, int argumentIndex) {
            this.constructor = constructor;
            this.argumentType = argumentType;
            this.argumentIndex = argumentIndex;
        }
    
        @Override
        public String getMessage() {
            return "Constructor " + constructor.getName() + " had a non-Option argument of type " + argumentType.getName() + " at position " + argumentIndex + " which was tried to supply with a null";
        }
    }
    
    static Object postProcessValue(Attribute<?, ?> attr, Object resultFromDb) {
        logger.debug("postProcessValue({},{})", attr, resultFromDb);
        if (attr == null) {
            logger.debug("Skipping processing since attr was null");
            // null is used as a placeholder in SelfAttribute and Constructors.IdProjection... Yeah, should use something else...
            return resultFromDb;
        }
        Object ret = transformPseudoResultToActualValue.ap(attr).andThen(
               wrapNullsToOptionsWhereAppropriate.ap(attr)).andThen(
               convertNullsToEmbeddableWhereRequired.ap(attr))
               .apply(resultFromDb);
        logger.debug("postProcessValue -> {}", ret);
        return ret;
    }
    
    static Iterable<Object> postProcessRow(List<Attribute<?,?>> projectionParameters, Iterable<Object> row) {
        logger.debug("postProcessRow({},{})", projectionParameters, row);
        Iterable<Object> ret = map(zip(projectionParameters, row), postProcessValue);
        logger.debug("postProcessRow -> {}", ret);
        return ret;
    }

    static <R> Iterable<R> transformAllRows(MetaJpaConstructor<?, R, ?> projection, Iterable<Iterable<Object>> rows) {
        logger.debug("transformAllRows({},{})", projection, rows);
        Iterable<R> ret = map(rows, ProjectionResultUtil_.<R>transformRow().ap(projection));
        logger.debug("transformAllRows -> {}", ret);
        return ret;
    }
    
    static <T> T transformRow(MetaJpaConstructor<?,T,?> projection, Iterable<Object> row) {
        logger.debug("transformRow({},{})", projection, row);
        row = postProcessRow(projection.getParameters(), row);
        // at this point there should be no nulls...
        for (Tuple2<Object, Integer> result: zip(row, range(0))) {
            if (result._1 == null) {
                throw new ProjectionResultUtil.NullValueButNonOptionConstructorArgumentException(projection.getClass(), projection.getConstructorParameterTypes().get(result._2), result._2);
            }
        }

        @SuppressWarnings("unchecked")
        T ret = ((MetaJpaConstructor<?,T,Object>)projection).apply(size(row) == 1 ? head(row) : Tuple.of(newArray(Object.class, row)));
        logger.debug("transformRow -> {}", ret);
        return ret;
    }
    
    static Object postProcessResult(Class<?> constructorParameterType, Attribute<?, ?> attr, List<Object> val) {
        logger.debug("postProcessResult({},{},{})", new Object[] {constructorParameterType, attr, val});
        Object ret;
        if (attr instanceof SingularAttribute) {
            if (!isRequiredByQueryAttribute(attr) && val.isEmpty()) {
                logger.debug("Optional SingularAttribute and empty resultList, returning null to be later replaced by None()");
                ret = null;
            } else {
                if (val.size() != 1) {
                    throw new IllegalArgumentException("Collection expected to be of size " + 1 + " but was: " + val);
                }
                ret = head(val);
            }
        } else {
            if (constructorParameterType.isAssignableFrom(List.class) && attr instanceof ListAttribute) {
                logger.debug("ListAttribute, or a Constructor expecting a List: {}", constructorParameterType.getName());
                ret = val;
            } else if (constructorParameterType.equals(SortedSet.class)) {
                logger.debug("Constructor expecting a SortedSet: {}", constructorParameterType.getName());
                ret = new TreeSet<Object>(val);
            } else if (constructorParameterType.isAssignableFrom(Set.class)) {
                logger.debug("Constructor expecting a Set: {}", constructorParameterType.getName());
                ret = newSet(val);
            } else {
                throw new UnsupportedOperationException("Not implemented: " + constructorParameterType);
            }
        }
        logger.debug("postProcessResult -> {}", ret);
        return ret;
    }

    static Object transformPseudoResultToActualValue(Attribute<?,?> attribute, Object resultFromDb) {
        logger.debug("transformPseudoResultToActualValue({},{})", attribute, resultFromDb);
        Object ret = resultFromDb;
        for (PseudoAttribute pseudo: unwrap(PseudoAttribute.class, attribute)) {
            logger.info("Replacing pseudo placeholder with actual value");
            ret = pseudo.getValueToReplaceResult(resultFromDb);
        }
        logger.debug("transformPseudoResultToActualValue -> {}", ret);
        return ret;
    }

    /** Wraps values to Some and nulls to None for optional parameters, leave others as is */
    static Object wrapNullsToOptionsWhereAppropriate(Attribute<?,?> attribute, Object resultFromDb) {
        logger.debug("wrapNullsToOptionsWhereAppropriate({},{})", attribute, resultFromDb);
        Object ret = isRequiredByQueryAttribute(attribute) || resultFromDb instanceof Option ? resultFromDb : Option.of(resultFromDb);
        logger.debug("wrapNullsToOptionsWhereAppropriate -> {}", ret);
        return ret;
    }
    
    /** Hibernate cannot handle embeddables with all-null values correctly, since it doesn't separate a missing embeddable and an existing all-null embeddable.
     *  So we instantiate the empty embeddable if the result has been left null but the attribute is required */
    static Object convertNullsToEmbeddableWhereRequired(Attribute<?,?> attribute, Object resultFromDb) {
        logger.debug("convertNullsToEmbeddableWhereRequired({},{})", attribute, resultFromDb);
        Object ret = resultFromDb;
        Option<? extends Attribute<?, ?>> embeddable = EmbeddableUtil.unwrapEmbeddableAttribute(attribute);
        if (embeddable.isDefined() && resultFromDb == null && isRequiredByMetamodel(attribute)) {
            Class<?> clazz = embeddable.get().getJavaType();
            logger.info("Instantiating an empty Embeddable {} in place of a null result", clazz);
            ret = EmbeddableUtil.instantiate(clazz);
        }
        logger.debug("convertNullsToEmbeddableWhereRequired -> {}", ret);
        return ret;
    }
    
}
