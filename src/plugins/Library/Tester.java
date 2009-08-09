/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library;

import plugins.Library.client.*;
import plugins.Library.index.*;
import plugins.Library.io.*;
import plugins.Library.serial.*;
import plugins.Library.serial.Serialiser.*;
import plugins.Library.util.*;

import freenet.keys.FreenetURI;

import java.util.*;
import java.io.*;


/**
** Various on-freenet tests.
**
** @author infinity0
*/
public class Tester {


	public static String runTest(String test) {
		if (test.equals("push_index")) {
			return testPushIndex();
		} else if (test.equals("push_progress")) {
			return testPushProgress();
		}
		return "tests: push_index, push_progress";
	}

	final public static String PAGE_START = "<html><head><meta http-equiv=\"refresh\" content=\"1\">\n<body>\n";
	final public static String PAGE_END  = "</body></html>\n";


	volatile static Thread push_progress_thread;
	volatile static SimpleProgress push_progress_progress = new SimpleProgress();
	volatile static Throwable push_progress_error;
	volatile static Date push_progress_start;
	volatile static FreenetURI push_progress_endURI;
	public static String testPushProgress() {
		if (push_progress_thread == null) {
			push_progress_thread = new Thread() {
				YamlReaderWriter yamlrw = new YamlReaderWriter();
				FreenetArchiver arx = new FreenetArchiver(Main.getPluginRespirator().getNode().clientCore, yamlrw, null, "text/yaml", 0x10000);

				public void run() {
					push_progress_start = new Date();
					Map<String, Integer> testmap = new TreeMap<String, Integer>();
					for(int i=0; i<0x10000; ++i) {
						testmap.put(""+i, i);
					}
					try {
						PushTask<Map<String, Integer>> task = new PushTask<Map<String, Integer>>(testmap);
						arx.pushLive(task, push_progress_progress);
						push_progress_endURI = (FreenetURI)task.meta;
					} catch (TaskAbortException e) {
						push_progress_error = e;
					}
				}
			};
			push_progress_thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
				public void uncaughtException(Thread t, Throwable e) {
					push_progress_error = e;
				}
			});
			push_progress_thread.start();
		}

		StringBuilder s = new StringBuilder();
		s.append(PAGE_START);
		appendTimeElapsed(s, push_progress_start);
		s.append("<p>").append(push_progress_progress.getStatus()).append("</p>\n");
		appendError(s, push_progress_error);
		appendResultURI(s, push_progress_endURI);
		s.append("<p>").append(push_progress_progress.partsDone());
		s.append(" ").append(push_progress_progress.partsTotal());
		s.append(" ").append(push_progress_progress.isTotalFinal()).append("</p>\n");
		s.append(PAGE_END);
		return s.toString();
	}


	volatile static Thread push_index_thread;
	volatile static String push_index_status = "";
	volatile static FreenetURI push_index_endURI;
	volatile static Date push_index_start;
	volatile static Throwable push_index_error;
	volatile static Set<String> push_index_words = new TreeSet<String>(Arrays.asList(
		"Lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipisicing",
		"elit", "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore",
		"et", "dolore", "magna", "aliqua.", "Ut", "enim", "ad", "minim",
		"veniam", "quis", "nostrud", "exercitation", "ullamco", "laboris", "nisi",
		"ut", "aliquip", "ex", "ea", "commodo", "consequat.", "Duis", "aute",
		"irure", "dolor", "in", "reprehenderit", "in", "voluptate", "velit",
		"esse", "cillum", "dolore", "eu", "fugiat", "nulla", "pariatur.",
		"Excepteur", "sint", "occaecat", "cupidatat", "non", "proident", "sunt",
		"in", "culpa", "qui", "officia", "deserunt", "mollit", "anim", "id", "est",
		"laborum."
	));
	public static String testPushIndex() {
		if (push_index_thread == null) {
			push_index_start = new Date();
			push_index_thread = new Thread() {
				BIndexSerialiser srl = new BIndexSerialiser();
				ProtoIndex idx;
				Random rand = new Random();

				public void run() {
					try {
						idx = new ProtoIndex(new FreenetURI("CHK@yeah"), "test");
					} catch (java.net.MalformedURLException e) {
						throw new AssertionError(e);
					}
					srl.setSerialiserFor(idx);

					for (String key: push_index_words) {
						SkeletonBTreeSet<TermEntry> entries = new SkeletonBTreeSet<TermEntry>(ProtoIndex.BTREE_NODE_MIN);
						srl.setSerialiserFor(entries);
						int n = rand.nextInt(0x200) + 0x200;
						try {
							for (int j=0; j<n; ++j) {
								TermEntry e = new TermPageEntry(key, new FreenetURI("CHK@fake~~~~" + UUID.randomUUID().toString().replace('-','~')));
								e.setRelevance((float)Math.random());
								entries.add(e);
							}
						} catch (java.net.MalformedURLException e) {
							throw new AssertionError(e);
						}
						// URGENT use a WriteableIndex and make ProtoIndex's ttab protected again
						idx.ttab.put(key, entries);
					}

					try {
						for (Map.Entry<String, SkeletonBTreeSet<TermEntry>> en: idx.ttab.entrySet()) {
							push_index_status = "Deflating entry " + en.getKey() + " (" + en.getValue().size() + " entries)";
							en.getValue().deflate();
						}
						push_index_status = "Deflating the term table";
						idx.ttab.deflate();
						PushTask<ProtoIndex> task1 = new PushTask<ProtoIndex>(idx);
						push_index_status = "Deflating the index";
						srl.push(task1);
						push_index_status = "Done!";
						push_index_endURI = (FreenetURI)task1.meta;
					} catch (TaskAbortException e) {
						push_index_error = e;
					}
				}
			};
			push_index_thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
				public void uncaughtException(Thread t, Throwable e) {
					push_index_error = e;
				}
			});
			push_index_thread.start();
		}

		StringBuilder s = new StringBuilder();
		s.append(PAGE_START);
		s.append("<p>Pushing index with terms: ").append(push_index_words.toString()).append("</p>\n");
		appendTimeElapsed(s, push_index_start);
		s.append("<p>").append(push_index_status).append("</p>");
		appendError(s, push_index_error);
		appendResultURI(s, push_index_endURI);
		s.append(PAGE_END);
		return s.toString();
	}


	public static void appendError(StringBuilder s, Throwable th) {
		if (th == null) { return; }
		ByteArrayOutputStream bs = new ByteArrayOutputStream(0x1000);
		th.printStackTrace(new PrintWriter(bs, true));
		s.append("<pre>").append(bs.toString()).append("</pre>\n");
	}

	public static void appendTimeElapsed(StringBuilder s, Date start) {
		if (start == null) { return; }
		s.append("<p>").append((new Date()).getTime() - start.getTime()).append("ms elapsed</p>\n");
	}

	public static void appendResultURI(StringBuilder s, FreenetURI u) {
		if (u != null) { s.append("<p>Result: ").append(u.toString()).append("</p>\n"); }
	}


}
