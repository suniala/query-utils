package fi.solita.utils.query;

import static fi.solita.utils.functional.Collections.newList;
import static fi.solita.utils.functional.Functional.concat;
import static fi.solita.utils.functional.Functional.flatMap;
import static fi.solita.utils.functional.Functional.grouped;
import static fi.solita.utils.functional.Functional.head;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Attribute.PersistentAttributeType;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;

import fi.solita.utils.codegen.NoMetadataGeneration;
import fi.solita.utils.functional.Transformer;
import fi.solita.utils.query.attributes.AttributeProxy;
import fi.solita.utils.query.attributes.LiteralAttribute;
import fi.solita.utils.query.attributes.OptionalAttribute;
import fi.solita.utils.query.attributes.SelfAttribute;

@NoMetadataGeneration
public abstract class QueryUtils {
    
    public static final class NoOrderingSpecifiedException extends RuntimeException {
        public NoOrderingSpecifiedException() {
            super("Paged query requires an Ordering. Either give Order.by:s as parameter (preferred), or use a query which defines ordering itself");
        }
    }

    public static final class RequiredAttributeMustNotHaveOptionTypeException extends RuntimeException {
        public RequiredAttributeMustNotHaveOptionTypeException(Attribute<?,?> attribute) {
            super(attribute.getDeclaringType().getJavaType().getSimpleName() + "." + attribute.getName() + ". Poista Project.option-käärintä ei-optionaaliselta parametrilta.");
        }
    }

    public static final class OptionalAttributeNeedOptionTypeException extends RuntimeException {
        public OptionalAttributeNeedOptionTypeException(Attribute<?,?> attribute) {
            super(attribute.getDeclaringType().getJavaType().getSimpleName() + "." + attribute.getName() + ". Käytä Project.option-metodia käärimään Attribuutti oikeaan tyyppiin.");
        }
    }

    private QueryUtils() {
        //
    }

    @SuppressWarnings("unchecked")
    public static <E> SingularAttribute<? super E, Id<E>> id(Class<? extends E> entityClass, EntityManager em) {
        EntityType<?> e = em.getMetamodel().entity(entityClass);
        return (SingularAttribute<? super E, Id<E>>) e.getId(e.getIdType().getJavaType());
    }

    public static void addListAttributeOrdering(CriteriaQuery<?> query, Expression<?> listAttributePath, CriteriaBuilder cb) {
        // "index" is a "function" in HQL that is used to order by the @Indexcolumn/@OrderColumn
        List<Order> orders = newList();
        if (query.getOrderList() != null) {
            orders.addAll(query.getOrderList());
        }
        orders.add(cb.asc(cb.function("index", null, listAttributePath)));
        query.orderBy(orders);
    }

    @SuppressWarnings("unchecked")
    public static <T> Selection<T> resolveSelection(CriteriaQuery<T> query) {
        if (query.getSelection() != null) {
            return query.getSelection();
        }
        Iterator<Root<?>> it = query.getRoots().iterator();
        if (it.hasNext()) {
            Root<?> root = it.next();
            if (!it.hasNext() && root.getJavaType().equals(query.getResultType())) {
                return (Root<T>) root;
            }
        }
        throw new RuntimeException("Could not resolve selection. Either specify entity-result type matching the single query root when constructing the query (cb.createQuery(FooEntity.class);) or do an explicit selection (query.select(fooEntity);)");
    }

    // If the selection targets an entity, it should always be a path. Right?
    public static <E> Path<E> resolveSelectionPath(CriteriaQuery<E> query) {
        return (Path<E>) resolveSelection(query);
    }

    public static From<?,?> resolveSelection(CriteriaQuery<?> from, CriteriaQuery<?> to) {
        if (from.getSelection() != null) {
            Selection<?> selection = (Selection<?>) from.getSelection();
            if (selection instanceof Root) {
                for (Root<?> root: to.getRoots()) {
                    if (root.getJavaType().equals(selection.getJavaType())) {
                        return (Root<?>) root;
                    }
                }
            }
            if (selection instanceof Join) {
                for (Root<?> root: to.getRoots()) {
                    for (Join<?,?> join: getAllJoins(root)) {
                        if (join.getAttribute().equals(((Join<?,?>)selection).getAttribute())) {
                            return (From<?,?>) join;
                        }
                    }
                }
            }
        }
        if (to.getRoots().size() == 1) {
            return (From<?, ?>)head(to.getRoots());
        }
        throw new RuntimeException("Could not resolve selection.");
    }

    public static <E> CriteriaQuery<E> applyOrder(CriteriaQuery<E> query, final Selection<E> selection, CriteriaBuilder cb) {
        if (selection instanceof Path<?> && ((Path<?>) selection).getModel() instanceof ListAttribute) {
            addListAttributeOrdering(query, (Path<?>) selection, cb);
        }
        return query;
    }

    public static <T> void checkOrdering(CriteriaQuery<T> query, Page page) throws NoOrderingSpecifiedException {
        if (page != Page.NoPaging && query.getOrderList().isEmpty()) {
            throw new NoOrderingSpecifiedException();
        }
    }

    public static final <T> Predicate inExpr(Path<? super T> path, Iterable<T> values, CriteriaBuilder cb) {
        Predicate pred = cb.disjunction();
        // oracle fails if more than 1000 parameters
        List<List<T>> groups = newList(grouped(values, 1000));
        for (List<T> g: groups) {
            pred = cb.or(pred, path.in(newList(g)));
        }
        return pred;
    }
    
    public static Iterable<Join<?,?>> getAllJoins(From<?, ?> parent) {
        return flatMap(parent.getJoins(), new Transformer<Join<?,?>,Iterable<Join<?,?>>>() {
            @Override
            public Iterable<Join<?, ?>> transform(Join<?, ?> source) {
                return concat(Arrays.<Join<?, ?>>asList(source), getAllJoins(source));
            }
        });
    }

    public static void checkOptionalAttributes(Attribute<?,?> param) {
        if (AttributeProxy.unwrap(param, SelfAttribute.class).isDefined() || AttributeProxy.unwrap(param, LiteralAttribute.class).isDefined()) {
            return;
        }
    
        boolean metaModelAttributeIsRequired = param.isCollection() || param instanceof SingularAttribute && (!((SingularAttribute<?,?>)param).isOptional() || ((SingularAttribute<?,?>)param).getPersistentAttributeType() == PersistentAttributeType.EMBEDDED);
        boolean queryAttributeIsRequired = !AttributeProxy.unwrap(param, OptionalAttribute.class).isDefined();
    
        if (!metaModelAttributeIsRequired && queryAttributeIsRequired) {
            throw new OptionalAttributeNeedOptionTypeException(param);
        } else if (metaModelAttributeIsRequired && !queryAttributeIsRequired) {
            throw new RequiredAttributeMustNotHaveOptionTypeException(param);
        }
    }
}
