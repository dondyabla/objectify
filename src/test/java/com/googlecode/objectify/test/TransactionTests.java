/*
 */

package com.googlecode.objectify.test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.logging.Logger;

import org.testng.annotations.Test;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.test.entity.Trivial;
import com.googlecode.objectify.test.util.TestBase;
import com.googlecode.objectify.test.util.TestObjectify;

import static com.googlecode.objectify.test.util.TestObjectifyService.fact;
import static com.googlecode.objectify.test.util.TestObjectifyService.ofy;

/**
 * Tests of transactional behavior.  Since many transactional characteristics are
 * determined by race conditions and other realtime effects, these tests are not
 * very thorough.  We will assume that Google's transactions work.
 *
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
public class TransactionTests extends TestBase
{
	/** */
	@SuppressWarnings("unused")
	private static Logger log = Logger.getLogger(TransactionTests.class.getName());

	/** */
	@Test
	public void testSimpleTransaction() throws Exception
	{
		fact().register(Trivial.class);

		Trivial triv = new Trivial("foo", 5);
		Key<Trivial> k = null;

		TestObjectify tOfy = fact().begin().transaction();
		try
		{
			k = tOfy.put(triv);
			tOfy.getTxn().commit();
		}
		finally
		{
			if (tOfy.getTxn().isActive())
				tOfy.getTxn().rollback();
		}

		Trivial fetched = ofy().get(k);

		assert fetched.getId().equals(k.getId());
		assert fetched.getSomeNumber() == triv.getSomeNumber();
		assert fetched.getSomeString().equals(triv.getSomeString());
	}

	/** */
	@Entity
	@Cache
	static class HasSimpleCollection
	{
		@Id Long id;
		List<String> stuff = new ArrayList<String>();
	}

	/** */
	@Test
	public void testInAndOutOfTransaction() throws Exception
	{
		fact().register(HasSimpleCollection.class);

		HasSimpleCollection simple = new HasSimpleCollection();

		TestObjectify nonTxnOfy = fact().begin();
		nonTxnOfy.put(simple);

		TestObjectify txnOfy = fact().begin().transaction();
		HasSimpleCollection simple2;
		try
		{
			simple2 = txnOfy.load().type(HasSimpleCollection.class).id(simple.id).now();
			simple2.stuff.add("blah");
			txnOfy.put(simple2);
			txnOfy.getTxn().commit();
		}
		finally
		{
			if (txnOfy.getTxn().isActive())
				txnOfy.getTxn().rollback();
		}

		nonTxnOfy.clear();
		HasSimpleCollection simple3 = nonTxnOfy.load().type(HasSimpleCollection.class).id(simple.id).now();

		assert simple2.stuff.equals(simple3.stuff);
	}

	/**
	 * This should theoretically test the case where the cache is being modified even after a concurrency failure.
	 * However, it doesn't seem to trigger even without the logic fix in ListenableFuture.
	 */
	@Test
	public void testConcurrencyFailure() throws Exception
	{
		fact().register(Trivial.class);

		Trivial triv = new Trivial("foo", 5);
		Key<Trivial> tk = fact().begin().put(triv);

		TestObjectify tOfy1 = fact().begin().transaction();
		TestObjectify tOfy2 = fact().begin().transaction();

		Trivial triv1 = tOfy1.get(tk);
		Trivial triv2 = tOfy2.get(tk);

		triv1.setSomeString("bar");
		triv2.setSomeString("shouldn't work");

		tOfy1.save().entity(triv1).now();
		tOfy2.save().entity(triv2).now();

		tOfy1.getTxn().commit();

		try {
			tOfy2.getTxn().commit();
			assert false;	// must throw exception
		} catch (ConcurrentModificationException ex) {}

		Trivial fetched = fact().begin().get(tk);

		// This will be fetched from the cache, and must not be the "shouldn't work"
		assert fetched.getSomeString().equals("bar");
	}

	/**
	 */
	@Test
	public void testTransactWork() throws Exception
	{
		fact().register(Trivial.class);

		final Trivial triv = new Trivial("foo", 5);
		ofy().put(triv);

		Trivial updated = ofy().transact(new Work<Trivial>() {
			@Override
			public Trivial run() {
				Trivial result = ofy().load().entity(triv).now();
				result.setSomeNumber(6);
				ofy().put(result);
				return result;
			}
		});
		assert updated.getSomeNumber() == 6;

		Trivial fetched = ofy().load().entity(triv).now();
		assert fetched.getSomeNumber() == 6;
	}

	/**
	 * Make sure that an async delete in a transaction fixes the session cache when the transaction is committed.
	 */
	@Test
	public void testAsyncDelete() throws Exception {
		fact().register(Trivial.class);

		final Trivial triv = new Trivial("foo", 5);

		// Make sure it's in the session (and memcache for that matter)
		this.putClearGet(triv);

		ofy().transact(new Work<Void>() {
			@Override
			public Void run() {
				// Load this, enlist in txn
				Trivial fetched = ofy().load().entity(triv).now();

				// Do this async, don't complete it manually
				ofy().delete().entity(fetched);

				return null;
			}
		});

		assert ofy().load().entity(triv).now() == null;
	}

	/** For transactionless tests */
	@Entity
	@Cache
	public static class Thing {
		@Id long id;
		String foo;
		public Thing() {}
		public Thing(long id) { this.id = id; this.foo = "foo"; }
	}

	/** */
	@Test
	public void testTransactionless() throws Exception
	{
		fact().register(Thing.class);

		for (int i=1; i<10; i++) {
			Thing th = new Thing(i);
			ofy().put(th);
		}

		ofy().transact(new Work<Void>() {
			@Override
			public Void run() {
				for (int i=1; i<10; i++)
					ofy().transactionless().load().type(Thing.class).id(i).now();

				ofy().put(new Thing(99));
				return null;
			}
		});
	}

	/**
	 */
	@Test
	public void testTransactionRollback() throws Exception
	{
		fact().register(Trivial.class);

		try {
			ofy().transact(new VoidWork() {
				@Override
				public void vrun() {
					Trivial triv = new Trivial("foo", 5);
					ofy().put(triv);
					throw new RuntimeException();
				}
			});
		} catch (RuntimeException ex) {}

		// Now verify that it was not saved
		Trivial fetched = ofy().load().type(Trivial.class).first().now();
		assert fetched == null;
	}

}