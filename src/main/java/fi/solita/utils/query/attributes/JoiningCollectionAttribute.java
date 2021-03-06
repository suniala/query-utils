package fi.solita.utils.query.attributes;

import static fi.solita.utils.functional.Collections.newList;
import static fi.solita.utils.functional.Functional.exists;
import static fi.solita.utils.functional.Functional.head;
import static fi.solita.utils.functional.Functional.last;

import java.util.Collection;
import java.util.List;

import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Bindable;
import javax.persistence.metamodel.CollectionAttribute;
import javax.persistence.metamodel.ManagedType;

import fi.solita.utils.query.QueryUtils;

@SuppressWarnings("unchecked")
class JoiningCollectionAttribute<E, R, A extends Attribute<E, Collection<R>> & Bindable<R>> extends PluralAttributeProxy<E,Collection<R>,R,A> implements CollectionAttribute<E,R>, JoiningAttribute {
    
    private final List<? extends Attribute<?, ?>> attributes;

    JoiningCollectionAttribute(Iterable<Attribute<?,?>> attrs) {
        super((A) last(attrs), CollectionType.COLLECTION, QueryUtils.<R>getElementType(last(attrs)));
        if (exists(JoiningAttribute.illegalContainedAttribute, attrs)) {
            throw new IllegalArgumentException("Cannot use attributes of types defined in JoiningAttribute.illegalContainedAttribute within JoiningAttributes!");
        }
        attributes = newList(attrs);
    }
    
    public List<? extends Attribute<?, ?>> getAttributes() {
        return attributes;
    }
    
    @Override
    public ManagedType<E> getDeclaringType() {
        return (ManagedType<E>) head(attributes).getDeclaringType();
    }
    
    @Override
    public CollectionType getCollectionType() {
        return CollectionType.COLLECTION;
    }
    
    @Override
    public Class<Collection<R>> getJavaType() {
        return (Class<Collection<R>>)(Object)Collection.class;
    }
    
    @Override
    public boolean isCollection() {
        return true;
    }
    
    @Override
    public String toString() {
        return JoiningAttribute.Constructors.joiningAttributeToString(this);
    }
}
