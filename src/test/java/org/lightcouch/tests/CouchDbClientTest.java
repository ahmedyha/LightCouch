/*
 * Copyright (C) 2011 Ahmed Yehia (ahmed.yehia.m@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lightcouch.tests;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lightcouch.Attachment;
import org.lightcouch.Changes;
import org.lightcouch.ChangesResult;
import org.lightcouch.CouchDbClient;
import org.lightcouch.CouchDbInfo;
import org.lightcouch.DesignDocument;
import org.lightcouch.DocumentConflictException;
import org.lightcouch.NoDocumentException;
import org.lightcouch.Page;
import org.lightcouch.ReplicationResult;
import org.lightcouch.ReplicatorDocument;
import org.lightcouch.Response;
import org.lightcouch.View;
import org.lightcouch.ViewResult;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Database integration tests.
 * @author Ahmed Yehia
 */
public class CouchDbClientTest {

	private static CouchDbClient dbClient;
	private static CouchDbClient dbClient2;
//	private static CouchDbClient dbClient3;

	@BeforeClass
	public static void setUpClass() {
		System.out.println("---------------------- Creating Clients");
		dbClient = new CouchDbClient();
		dbClient2 = new CouchDbClient("couchdb-2.properties");
//		dbClient3 = new CouchDbClient("lightcouch-db-test-3", true, "http", "127.0.0.1", 5984, null, null);
	}

	@AfterClass
	public static void tearDownClass() {
		dbClient.shutdown();
		dbClient2.shutdown();
//		dbClient3.shutdown();
	}

	// ----------------------------------------------------------------- Find

	@Test(expected=IllegalArgumentException.class)
	public void testFindThrowsIllegalArgumentException() {
		dbClient.find(Foo.class, ""); // empty or null arguments
	}

	@Test(expected=NoDocumentException.class)
	public void testFindThrowsNoDocumentException() {
		dbClient.find(Foo.class, UUID.randomUUID().toString()); // id value of a new UUID should never be found
	}

	@Test
	public void testFind() throws IOException {
		System.out.println("------------------------------- Testing find()");

		// save a new doc and obtain the response
		Response resp = dbClient.save(new Foo());
		Foo foo = dbClient.find(Foo.class, resp.getId()); // find by id
		assertNotNull(foo);

		foo = dbClient.find(Foo.class, resp.getId(), resp.getRev()); // find by id & rev
		assertNotNull(foo);

		InputStream inputStream = dbClient.find(resp.getId()); // find by id, get input stream
		assertTrue(inputStream.read() != -1); // check the stream is not empty
		inputStream.close();

		inputStream = dbClient.find(resp.getId(), resp.getRev());
		assertTrue(inputStream.read() != -1);
		inputStream.close();
	}

	@Test
	public void testFindJSON() throws IOException {
		System.out.println("------------------------------- Testing find JSON");
		final String TITLE = "Json Title";
		Foo foo = new Foo(UUID.randomUUID().toString(), TITLE, 1);
		Response response = dbClient.save(foo);
		JsonObject json = dbClient.find(JsonObject.class, response.getId());
		assertEquals(json.get("title").getAsString(), TITLE);
	}

	@Test
	public void testContains() {
		System.out.println("------------------------------- Testing contains()");
		String id = UUID.randomUUID().toString(); // new UUID
		boolean found = dbClient.contains(id);
		assertFalse(found);

		dbClient.save(new Foo(id));
		found = dbClient.contains(id);
		assertTrue(found);
	}

	// ----------------------------------------------------------------- Save/Update

	@Test(expected=IllegalArgumentException.class)
	public void testSaveThrowsIllegalArgumentException() {
		dbClient.save(null); // trying to save an invalid object
	}

	@Test(expected=IllegalArgumentException.class)
	public void testSaveThrowsIllegalArgumentException_2() {
		Foo foo = new Foo();
		foo.set_rev("some-rev"); // new docs should not have a revision value assigned
		dbClient.save(foo);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testUpdateThrowsIllegalArgumentException_3() {
		Foo foo = new Foo();
		dbClient.update(foo); // updating docs should have both id and revision values assigned
	}

	@Test(expected=DocumentConflictException.class)
	public void testSaveThrowsDocumentConflictException() {
		Foo foodb1 = new Foo(UUID.randomUUID().toString(), "a title", 0);
		dbClient.save(foodb1);
		dbClient.save(foodb1); // saving the same doc twice (i.e with the same _id), gives 409 conflict
	}

	@Test(expected=DocumentConflictException.class)
	public void testUpdateThrowsDocumentConflictException() {
		Response resp = dbClient.save(new Foo());         // save a new doc
		Foo foo = dbClient.find(Foo.class, resp.getId()); // find it, by id
		dbClient.update(foo); // should be ok, foo rev is the latest
		dbClient.update(foo); // should fail with 409, foo rev is out of date
	}

	@Test
	public void testSaveAndUpdate() throws IOException {
		System.out.println("------------------------------- Testing Save/Update");

    	final String TITLE = "new title";
    	Response resp = dbClient.save(new Foo());         // save a new doc
    	Foo foo = dbClient.find(Foo.class, resp.getId()); // find it, by id
    	foo.setTitle(TITLE);                              // modify with some value
    	foo.set_rev(resp.getRev());						  // assign it the correct _rev
    	dbClient.update(foo); 							  // update should be ok

    	foo = dbClient.find(Foo.class, resp.getId());     // find it again after update, by id
    	assertThat(foo.getTitle(), is(TITLE));                  // check it has the correct value assigned
    	assertThat(foo.getTitle(), is(not("different title"))); // check it doesn't have incorrect values assigned

	}

	@Test
	public void testSaveMap() {
		System.out.println("------------------------------- Testing Save Map");
    	final String ID = UUID.randomUUID().toString();
    	final String KEY = "title";
    	final String VALUE = "title-value";

    	Map<String, Object> map = new HashMap<String, Object>();
    	map.put("_id", ID);
    	map.put(KEY, VALUE);

    	Response resp = dbClient.save(map);  // save the Map
    	assertNotNull(resp.getRev()); // check we got back a rev after save
	}

	@Test
	public void testSaveJSON() {
		System.out.println("------------------------------- Testing Save JSON");
		JsonObject json = new JsonObject();
		json.addProperty("_id", UUID.randomUUID().toString());
		json.add("an-array", new JsonArray());
		dbClient.save(json);
	}

	@Test
	public void testSaveAttachmentInline() {
		System.out.println("------------------------------- Testing Save Attachment - Inline");
		// init 2 attachments
		Attachment attachment1 = new Attachment();
		attachment1.setContentType("text/plain");
		attachment1.setData("VGhpcyBpcyBhIGJhc2U2NCBlbmNvZGVkIHRleHQ=");

		Attachment attachment2 = new Attachment();
		attachment2.setContentType("text/plain");
		byte[] bytes = new byte[] {65, 32, 100, 101, 109, 111, 32, 46, 116, 120, 116, 32, 97, 116, 116, 97, 99, 104, 109, 101, 110, 116, 46, 10};
		attachment2.setData(Base64.encodeBase64String(bytes)); // binary to base64 encoding using Apache Codec

		Foo foo = new Foo();
		Map<String, Attachment> attachments = new HashMap<String, Attachment>();
		attachments.put("foo.txt", attachment1);
		attachments.put("foo2.txt", attachment2);
		foo.set_attachments(attachments);

		dbClient.save(foo);

		Bar bar = new Bar(); // Bar extends Document
		bar.addAttachment("bar.txt", attachment1);
		bar.addAttachment("bar2.txt", attachment2);

		dbClient.save(bar);
	}

	@Test
	public void testSaveAttachmenStandalone() throws IOException {
		System.out.println("------------------------------- Testing Save Attachment - Standalone");
		final String fileName = "foo.txt";
		// represents a text file containing: "A demo .txt attachment."
		byte[] bytesToDB = new byte[] {65, 32, 100, 101, 109, 111, 32, 46, 116, 120, 116, 32, 97, 116, 116, 97, 99, 104, 109, 101, 110, 116, 46, 10};

		// save the file as document attachment
		ByteArrayInputStream bytesIn = new ByteArrayInputStream(bytesToDB);
		Response resp = dbClient.saveAttachment(bytesIn, fileName, "text/plain"); // creates a new document, and saves the attachment

		// retrieve the saved attachment, extract bytes for comparison
		InputStream in = dbClient.find(String.format("%s/%s", resp.getId(), fileName));
		ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		int n;
		while ((n = in.read()) != -1) {
			bytesOut.write(n);
		}
		bytesOut.flush();
		in.close();

		byte[] bytesFromDB = bytesOut.toByteArray();
		assertArrayEquals(bytesToDB, bytesFromDB); // ensure we got the exact bytes we just saved
	}

	// ----------------------------------------------------------------- Remove
	@Test(expected=IllegalArgumentException.class)
	public void testRemoveThrowsIllegalArgumentException() {
		dbClient.remove(null); // invalid args
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRemoveThrowsIllegalArgumentException_2() {
		dbClient.remove("some-id", null); // on delete, must specify both id rev
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRemoveThrowsIllegalArgumentException_3() {
		Foo foo = new Foo();
		foo.set_id("some-id"); // on delete, must specify both id rev
		dbClient.remove(foo);
	}

	@Test(expected=NoDocumentException.class)
	public void testRemoveThrowsNoDocumentException() {
		Foo foo = new Foo();
		foo.set_id(UUID.randomUUID().toString());  // set a new _id
		foo.set_rev(UUID.randomUUID().toString()); // set a new _rev
		// trying to delete a document that does not exist, CouchDB returns error 404.
		dbClient.remove(foo);
	}

	@Test
	public void testRemove() {
		System.out.println("------------------------------- Testing remove()");

		Response responseSave = dbClient.save(new Foo());         // save a fresh object
		Foo foo = dbClient.find(Foo.class, responseSave.getId()); // find the saved doc, by id
		Response responseRemove = dbClient.remove(foo);           // remove the doc

		// saved doc rev should not equal deleted doc rev (of the same doc)
    	assertThat(responseSave.getRev(), is(not(responseRemove.getRev())));

	}

	// ------------------------------------------------------------------------- Views

	private void initView() {
		try {
			Foo foo = null;

			foo = new Foo("hello-world-1", "hello-world-1", 1);
			foo.setTags(Arrays.asList(new String[] {"couchdb", "views"}));
			foo.setComplexDate(new int[] {2011, 10, 15});
			dbClient.save(foo);

			foo = new Foo("hello-world-2", "hello-world-2", 2);
			foo.setTags(Arrays.asList(new String[] {"java", "couchdb"}));
			foo.setComplexDate(new int[] {2011, 10, 15});
			dbClient.save(foo);

			foo = new Foo("hello-world-3", "hello-world-3", 3);
			foo.setComplexDate(new int[] {2013, 12, 17});
			dbClient.save(foo);

			DesignDocument exampleDoc = dbClient.design().getFromDesk("example");
			dbClient.design().synchronizeWithDb(exampleDoc);

		} catch (DocumentConflictException e) {}
	}

	@Test()
	public void testViewIncludeDocs() {
		initView();
		System.out.println("------------------------------- Test View with include docs");
		View view = dbClient.view("example/foo").includeDocs(true).key("hello-world-1");
		List<Foo> foos = view.query(Foo.class);
		assertThat(foos.size(), is(not(0)));
	}

	@Test()
	public void testViewByKeyRange() {
		initView();
		System.out.println("------------------------------- Test View by key range");
		List<Foo> foos = dbClient.view("example/foo")
		.includeDocs(true).startKey("hello-world-1").endKey("hello-world-2").query(Foo.class);
		assertThat("Only 2 docs should be returned.", foos.size(), is(2));
	}

	@Test()
	public void testViewByComplexKey() {
		initView();
		System.out.println("------------------------------- Test View by complex key ");

		int[] requestComplexKey = new int[] {2011, 10, 15};
		ViewResult<int[], String, Foo> viewResult = dbClient.view("example/by_date").key(requestComplexKey).updateSeq(true).reduce(false).queryView(int[].class, String.class, Foo.class);
		int[] resultComplexKey = viewResult.getRows().get(0).getKey();

		assertThat(viewResult.getRows().size(), is(2)); // we have 2 docs with this complex key
		assertTrue("Result key should match that of the request key.", Arrays.equals(requestComplexKey, resultComplexKey));
	}

	@Test()
	public void testViewWithScalarValues() {
		initView();
		System.out.println("------------------------------- Test View return scalar values");

		int allTags = dbClient.view("example/by_tag").queryForInt();
		assertThat(allTags, is(4));     // we have 4 tags defined

		long couchDbTags = dbClient.view("example/by_tag").key("couchdb").queryForLong();
		assertThat(couchDbTags, is(2L)); // we have 2 occurances of couchdb tag

		String javaTags = dbClient.view("example/by_tag").key("java").queryForString();
		assertThat(javaTags, is("1")); // we have 1 occurance of java tag
	}

	@Test(expected=NoDocumentException.class)
	public void testViewThrowsNoDocumentException() {
		System.out.println("------------------------------- Test View throws exception");
		dbClient.view("example/by_tag").key("javax").queryForInt();
	}

	@Test
	public void testViewByGroupLevel() {
		System.out.println("------------------------------- Test View by group level");
		ViewResult<int[], Integer, Foo> viewResult = dbClient.view("example/by_date").groupLevel(2).queryView(int[].class, Integer.class, Foo.class);
		assertThat(viewResult.getRows().size(), is(2)); // yields two rows: (key:value) [2011,10]:2 and [2013,12]:1
		assertThat(viewResult.getRows().get(0).getKey(), is(new int[] {2011, 10})); // check 1st row
		assertThat(viewResult.getRows().get(0).getValue(), is(2));
	}

	// ------------------------------------------------------------------ Replication

	@Test
	public void testReplicate() {
		System.out.println("------------------------------- Testing Replication");
		ReplicationResult result = dbClient.replication()
			.createTarget(true)
			.source(dbClient.getDBUri().toString())
			.target(dbClient2.getDBUri().toString())
			.trigger();
    	assertThat(result.getHistories().size(), is(not(0)));
	}

	@Test
	public void testReplicatorDB() throws InterruptedException {
		String version = dbClient.context().serverVersion();
		if(version.startsWith("0") || version.startsWith("1.0")) {
			return; // skip test for older CouchDB releases not supporting the replicator db.
		}
		System.out.println("------------------------------- Testing Replicator DB");
		Response saveResponse = dbClient.replicator()
				.source(dbClient.getDBUri().toString())
				.target(dbClient2.getDBUri().toString())
				.continuous(true)
				.createTarget(true)
				.save();

		Thread.sleep(300L); // allow some time for the document to update itself in the DB

		ReplicatorDocument findDocument = dbClient.replicator()
				.replicatorDocId(saveResponse.getId())
				.find();
		assertThat(saveResponse.getId(), is(findDocument.getId())); // ensure we found the same doc we just created

		List<ReplicatorDocument> docs = dbClient.replicator().findAll();
		assertThat(docs.size(), is(not(0)));

		Response removeResponse = dbClient.replicator()
				.replicatorDocId(findDocument.getId())
				.replicatorDocRev(findDocument.getRevision())
				.remove();
		assertThat(removeResponse.getId(), is(saveResponse.getId())); // ensure we deleted the same doc we created
	}

	@Test
	public void testReplicationConflict() {
		System.out.println("------------------------------- Replicate Conflict Test Started");

		DesignDocument conflictsDoc = dbClient.design().getFromDesk("conflicts");
		dbClient2.design().synchronizeWithDb(conflictsDoc);

		String anId = UUID.randomUUID().toString();
		Foo foodb1 = new Foo(anId, "title", 0);
		dbClient.save(foodb1); // save to db 1

		dbClient.replication()
			.source(dbClient.getDBUri().toString())
			.target(dbClient2.getDBUri().toString())
			.trigger();

		Foo foodb2 = dbClient2.find(Foo.class, anId); // find in db 2
		foodb2.setTitle("new title"); // modify
		dbClient2.update(foodb2); // update in db 2

		foodb1 = dbClient.find(Foo.class, anId); // find in db 1
		foodb1.setTitle("another title"); // modify
		dbClient.update(foodb1); // update in db 1

		dbClient.replication().source(dbClient.getDBUri().toString()).target(dbClient2.getDBUri().toString()).trigger();

		ViewResult<String[], String, Void> conflicts = dbClient2.view("conflicts/conflict").queryView(String[].class, String.class, Void.class);
		assertThat(conflicts.getRows().size(), is(not(0))); // there should be conflicts
	}

	// --------------------------------------------------------------- context API
	@Test
	public void testDbInfo() {
		System.out.println("------------------------------- Testing info()");
		CouchDbInfo dbInfo = dbClient.context().info();
		assertNotNull(dbInfo);
	}

	@Test
	public void testDbServerVersion() {
		System.out.println("------------------------------- Testing serverVersion()");
		String version = dbClient.context().serverVersion();
		assertNotNull(version);
	}

	@Test
	public void testCompactDb() {
		System.out.println("------------------------------- Testing compact()");
		dbClient.context().compact();
	}

	@Test
	public void testGetAllDbs() {
		System.out.println("------------------------------- Testing getAllDbs()");
		List<String> allDbs = dbClient.context().getAllDbs();
		assertThat(allDbs.size(), is(not(0)));
	}

	@Test
	public void testEnsureFullCommit() {
		System.out.println("------------------------------- Testing ensureFullCommit()");
		dbClient.context().ensureFullCommit();
	}

	// ------------------------------------------------------- Design documents
	@Test
	public void testDesignDocs() {
		System.out.println("------------------------------- Testing Design documents");
		dbClient.design().synchronizeAllWithDb();

		DesignDocument exampleDoc = dbClient.design().getFromDesk("example");
		DesignDocument exampleDoc1 = dbClient.design().getFromDesk("example");

		assertTrue(exampleDoc.equals(exampleDoc1)); // same doc

		DesignDocument documentFromDb = dbClient.design().getFromDb("_design/example");
		assertTrue(exampleDoc.equals(documentFromDb)); // same doc, from db and desk

		exampleDoc.getViews().get("foo").put("map", "new map function()");
		assertFalse(exampleDoc.equals(documentFromDb));

		List<DesignDocument> designDocuments =  dbClient.design().getAllFromDesk();
		assertThat(designDocuments.size(), is(not(0)));

		DesignDocument documentFromDb2 = dbClient.design().getFromDb(documentFromDb.getId(), documentFromDb.getRevision());
		assertEquals(documentFromDb.getRevision(), documentFromDb2.getRevision());
	}

	// ------------------------------------------------------- Pagination
	@Test
	public void testPagination() {
		System.out.println("------------------------------- Testing Pagination");
		// DB may already contains records, insert few for safety
		for (int i = 0; i < 10; i++) {
			dbClient.save(new Foo(UUID.randomUUID().toString(), "paging title", 1));
		}

		final int rowsPerPage = 3;
		// first page, page #1, rows: 1 - 3
		Page<Foo> page = dbClient.view("example/foo").queryPage(rowsPerPage, null, Foo.class);
		assertFalse(page.isHasPrevious());
		assertTrue(page.isHasNext());
		assertThat(page.getResultFrom(), is(1));
		assertThat(page.getResultTo(), is(3));
		assertThat(page.getPageNumber(), is(1));
		assertThat(page.getResultList().size(), is(3));
		//-----------------------------------------

		// prepare to go next
		String param = page.getNextParam();
		// next page, page #2, rows: 4 - 6
		page = dbClient.view("example/foo").queryPage(rowsPerPage, param, Foo.class);
		assertTrue(page.isHasPrevious());
		assertTrue(page.isHasNext());
		assertThat(page.getResultFrom(), is(4));
		assertThat(page.getResultTo(), is(6));
		assertThat(page.getPageNumber(), is(2));
		assertThat(page.getResultList().size(), is(3));
		//-----------------------------------------

		// prepare to go previous
		param = page.getPreviousParam();
		// previous page, page #1, rows: 1 - 3
		page = dbClient.view("example/foo").queryPage(rowsPerPage, param, Foo.class);
		assertFalse(page.isHasPrevious());
		assertTrue(page.isHasNext());
		assertThat(page.getResultFrom(), is(1));
		assertThat(page.getResultTo(), is(3));
		assertThat(page.getPageNumber(), is(1));
		assertThat(page.getResultList().size(), is(3));
	}

	// ------------------------------------------------------- Changes
	@Test
	public void testChanges() {
		System.out.println("------------------------------- Testing Change Notifications");
		dbClient.save(new Foo(UUID.randomUUID().toString(), "title", 1)); // save a document

		// feed type normal
		ChangesResult result = dbClient.changes()
				.includeDocs(true)
				.limit(1)
				.getChanges();
		assertThat(result.getResults().size(), is(1));

		CouchDbInfo dbInfo = dbClient.context().info();
		String since = dbInfo.getUpdateSeq();

		// feed type continuous
		Changes changes = dbClient.changes()
				.includeDocs(true)
				.since(since)
				.heartBeat(30000)
				.continuousChanges();

		Response resp = dbClient.save(new Foo(UUID.randomUUID().toString(), "title", 1));

		while (changes.hasNext()) {
			ChangesResult.Row feed = changes.next();
			String docId = feed.getId();
			assertEquals(resp.getId(), docId);
			changes.stop();
		}
	}
}

