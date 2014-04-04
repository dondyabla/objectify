package com.googlecode.objectify.impl;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;



/**
 * <p>Gives us a slightly more organized interface for manipulating keys. While this is part of Objectify's
 * public interface, you probably shouldn't use it. It's subject to change without notice. If you want to
 * work with keys, use the Key.create() methods.</p>
 *
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
public class Keys
{
	private final Registrar registrar;

	/** */
	public Keys(Registrar registrar) {
		this.registrar = registrar;
	}

//	/**
//	 * @return the Key<?> of the pojo entity
//	 */
//	public <T> Key<T> keyOf(T entity) {
//		return Key.create(getMetadataForEntity(entity).getKeyMetadata().getRawKey(entity));
//	}

	/**
	 * @return the Key<?> for a registered pojo entity.
	 */
	public <T> Key<T> keyOf(T pojo) {
		return Key.create(rawKeyOf(pojo));
	}

	/**
	 * @return the native datastore key for a registered pojo entity.
	 */
	public com.google.appengine.api.datastore.Key rawKeyOf(Object pojo) {
		return getMetadataSafe(pojo).getRawKey(pojo);
	}

	/**
	 * @return the metadata for the pojo, returning null if type is not registered
	 */
	@SuppressWarnings("unchecked")
	public <T> KeyMetadata<T> getMetadata(T pojo) {
		return (KeyMetadata<T>)getMetadata(pojo.getClass());
	}

	/**
	 * @return the metadata for the pojo, returning null if type is not registered
	 */
	@SuppressWarnings("unchecked")
	private <T> KeyMetadata<T> getMetadata(Class<T> clazz) {
		EntityMetadata<T> em = registrar.getMetadata(clazz);
		return em == null ? null : em.getKeyMetadata();
	}

	/**
	 * @return the metadata for a registered pojo, or throw exception if none
	 * @throws IllegalStateException if the pojo class has not been registered
	 */
	public <T> KeyMetadata<T> getMetadataSafe(Class<T> clazz) {
		return registrar.getMetadataSafe(clazz).getKeyMetadata();
	}

	/**
	 * @return the metadata for a registeerd pojo, or throw exception if none
	 * @throws IllegalStateException if the pojo class has not been registered
	 */
	@SuppressWarnings("unchecked")
	public <T> KeyMetadata<T> getMetadataSafe(T pojo) {
		return (KeyMetadata<T>)getMetadataSafe(pojo.getClass());
	}

	/**
	 * @return the metadata for a registered pojo, or null if there is none
	 */
	@SuppressWarnings("unchecked")
	public <T> KeyMetadata<T> getMetadata(Key<T> key) {
		EntityMetadata<T> em = registrar.getMetadata(key.getKind());
		return em == null ? null : em.getKeyMetadata();
	}

	/**
	 * <p>Gets the Key<T> given an object that might be a Key, Key<T>, or entity.</p>
	 *
	 * @param keyOrEntity must be a Key, Key<T>, or registered entity.
	 * @throws NullPointerException if keyOrEntity is null
	 * @throws IllegalArgumentException if keyOrEntity is not a Key, Key<T>, or registered entity
	 */
	@SuppressWarnings("unchecked")
	public <T> Key<T> anythingToKey(Object keyOrEntity) {

		if (keyOrEntity instanceof Key<?>)
			return (Key<T>)keyOrEntity;
		else if (keyOrEntity instanceof com.google.appengine.api.datastore.Key)
			return Key.create((com.google.appengine.api.datastore.Key)keyOrEntity);
		else if (keyOrEntity instanceof Ref)
			return ((Ref<T>)keyOrEntity).key();
		else
			return keyOf((T)keyOrEntity);
	}

	/**
	 * <p>Gets the raw datstore Key given an object that might be a Key, Key<T>, or entity.</p>
	 *
	 * @param keyOrEntity must be a Key, Key<T>, or registered entity.
	 * @throws NullPointerException if keyOrEntity is null
	 * @throws IllegalArgumentException if keyOrEntity is not a Key, Key<T>, or registered entity
	 */
	public com.google.appengine.api.datastore.Key anythingToRawKey(Object keyOrEntity) {

		if (keyOrEntity instanceof com.google.appengine.api.datastore.Key)
			return (com.google.appengine.api.datastore.Key)keyOrEntity;
		else if (keyOrEntity instanceof Key<?>)
			return ((Key<?>)keyOrEntity).getRaw();
		else if (keyOrEntity instanceof Ref)
			return ((Ref<?>)keyOrEntity).key().getRaw();
		else
			return rawKeyOf(keyOrEntity);
	}
}
