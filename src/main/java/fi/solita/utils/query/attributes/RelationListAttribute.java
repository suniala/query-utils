package fi.solita.utils.query.attributes;

import java.util.List;

import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Bindable;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.Type;

import fi.solita.utils.query.IEntity;
import fi.solita.utils.query.meta.MetaJpaConstructor;

class RelationListAttribute<E, R, A extends Attribute<E, List<R>> & Bindable<R>> extends PluralAttributeProxy<E, List<R>, R, A> implements ListAttribute<E,R>, AdditionalQueryPerformingAttribute {
    private final MetaJpaConstructor<? extends IEntity, R, ?> constructor;

    @SuppressWarnings("unchecked")
    public <E2 extends IEntity> RelationListAttribute(ListAttribute<? super E, ? super E2> attribute, MetaJpaConstructor<? super E2, R, ?> constructor) {
        super((A)(Object)attribute, CollectionType.LIST, (Type<R>)attribute.getElementType());
        this.constructor = (MetaJpaConstructor<? extends IEntity, R, ?>) constructor;
    }

    @Override
    public MetaJpaConstructor<? extends IEntity, R, ?> getConstructor() {
        return constructor;
    }
}