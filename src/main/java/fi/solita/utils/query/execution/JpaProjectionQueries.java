package fi.solita.utils.query.execution;

import static fi.solita.utils.functional.Collections.newList;
import static fi.solita.utils.functional.Functional.head;
import static fi.solita.utils.functional.Functional.headOption;
import static fi.solita.utils.functional.FunctionalImpl.map;
import static fi.solita.utils.functional.Option.None;
import static fi.solita.utils.functional.Option.Some;
import static fi.solita.utils.query.QueryUtils.applyOrder;
import static fi.solita.utils.query.QueryUtils.resolveSelection;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;

import fi.solita.utils.functional.Function0;
import fi.solita.utils.functional.Option;
import fi.solita.utils.query.IEntity;
import fi.solita.utils.query.JpaCriteriaCopy;
import fi.solita.utils.query.Order;
import fi.solita.utils.query.Page;
import fi.solita.utils.query.QueryUtils;
import fi.solita.utils.query.QueryUtils.NoOrderingSpecifiedException;
import fi.solita.utils.query.backend.JpaCriteriaQueryExecutor;
import fi.solita.utils.query.codegen.MetaJpaConstructor;
import fi.solita.utils.query.projection.ProjectionHelper;
import fi.solita.utils.query.projection.ProjectionUtil;
import fi.solita.utils.query.projection.ProjectionUtil_;

public class JpaProjectionQueries {

    private final ProjectionHelper projectionSupport;
    private final Function0<EntityManager> em;
    private final JpaCriteriaQueryExecutor queryExecutor;

    public JpaProjectionQueries(Function0<EntityManager> em, ProjectionHelper projectionSupport, JpaCriteriaQueryExecutor queryExecutor) {
        this.em = em;
        this.projectionSupport = projectionSupport;
        this.queryExecutor = queryExecutor;
    }

    public <E extends IEntity, R> R get(CriteriaQuery<E> query, MetaJpaConstructor<? super E,R, ?> constructor) throws NoResultException, NonUniqueResultException {
        CriteriaQuery<Object> q = em.apply().getCriteriaBuilder().createQuery();
        JpaCriteriaCopy.copyCriteriaWithoutSelect(query, q, em.apply().getCriteriaBuilder());
        From<?,E> selection = QueryUtils.resolveSelection(query, q);
        q.multiselect(projectionSupport.prepareProjectingQuery(constructor, selection));
        
        List<Iterable<Object>> res = newList();
        res.add(ProjectionUtil.objectToObjectList(queryExecutor.get(q)));
        return head(projectionSupport.finalizeProjectingQuery(constructor, res));
    }

    public <E extends IEntity, R> Option<R> find(CriteriaQuery<E> query, MetaJpaConstructor<? super E,R, ?> constructor) throws NonUniqueResultException {
        try {
            return Some(get(query, constructor));
        } catch (NoResultException e) {
            return None();
        }
    }

    public <E extends IEntity,R> Option<R> findFirst(CriteriaQuery<E> query, MetaJpaConstructor<? super E,R, ?> constructor) throws NoOrderingSpecifiedException {
        return headOption(getMany(query, constructor, Page.FIRST.withSize(1)));
    }

    public <E extends IEntity,R> Option<R> findFirst(CriteriaQuery<E> query, MetaJpaConstructor<? super E,R, ?> constructor, Iterable<? extends Order<? super E,?>> ordering) {
        return headOption(getMany(query, constructor, Page.FIRST.withSize(1), ordering));
    }

    public <E extends IEntity,R> Collection<R> getMany(CriteriaQuery<E> query, MetaJpaConstructor<? super E,R, ?> constructor) throws NoOrderingSpecifiedException {
        return getMany(query, constructor, Page.NoPaging);
    }

    public <E extends IEntity,R> List<R> getMany(CriteriaQuery<E> query, MetaJpaConstructor<? super E,R, ?> constructor, Page page) throws NoOrderingSpecifiedException {
        QueryUtils.applyOrder(query, resolveSelection(query), em.apply().getCriteriaBuilder());
        QueryUtils.checkOrdering(query, page);
        List<Order<? super E,?>> noOrdering = Collections.emptyList();
        return getMany(query, constructor, page, noOrdering);
    }

    public <E extends IEntity,R> List<R> getMany(CriteriaQuery<E> query, MetaJpaConstructor<? super E,R, ?> constructor, Iterable<? extends Order<? super E,?>> ordering) {
        return getMany(query, constructor, Page.NoPaging, ordering);
    }

    public <E extends IEntity,R> List<R> getMany(CriteriaQuery<E> query, MetaJpaConstructor<? super E,R, ?> constructor, Page page, Iterable<? extends Order<? super E,?>> ordering) {
        CriteriaQuery<Object> q = em.apply().getCriteriaBuilder().createQuery();
        JpaCriteriaCopy.copyCriteriaWithoutSelect(query, q, em.apply().getCriteriaBuilder());
        From<?,E> selection = resolveSelection(query, q);

        @SuppressWarnings("unchecked")
        CriteriaQuery<Object> ordered = (CriteriaQuery<Object>)(Object)applyOrder((CriteriaQuery<E>)(Object)q, selection, ordering, em.apply().getCriteriaBuilder());

        q.multiselect(projectionSupport.prepareProjectingQuery(constructor, selection));
        
        List<Object> results = queryExecutor.getMany(ordered, page);
        return projectionSupport.finalizeProjectingQuery(constructor, map(results, ProjectionUtil_.objectToObjectList));
    }
}
