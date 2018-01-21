package com.ittera.cometa.concentrator;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
 */
public class PeerOptionsTest {

	@Test
	public void parse_noArgs_optsNullAndFalse() {

		String line = "";
		PeerOptions opts = PeerOptions.parse(line.split(" "));

		assertNull(opts.inLog);
		assertNull(opts.outLog);
		assertFalse(opts.offsetGiven);
		assertNull(opts.offset);
	}

	@Test
	public void parse_nonNumericOffset_exceptionThrown() {

		String line = "--offset-start ten";
		try {
			PeerOptions opts = PeerOptions.parse(line.split(" "));
			fail("Should have thrown a NumberFormatException");
		} catch (NumberFormatException ex) {
			// ok
		}

	}

	@Test
	public void parse_argsWithOffset_offsetParsed() {

		String line = "--offset-start 10";
		PeerOptions opts = PeerOptions.parse(line.split(" "));

		assertNull(opts.inLog);
		assertNull(opts.outLog);
		assertTrue(opts.offsetGiven);
		assertEquals(Long.valueOf(10), opts.offset);
	}

	@Test
	public void parse_argsWithInLog_inLogParsed() {

		String line = "--read-log myInputLog";
		PeerOptions opts = PeerOptions.parse(line.split(" "));

		assertNotNull(opts.inLog);
		assertEquals("myInputLog", opts.inLog.getName());
		assertNull(opts.outLog);
		assertFalse(opts.offsetGiven);
		assertNull(opts.offset);
	}

	@Test
	public void parse_argsWithOutLog_outLogParsed() {

		String line = "--write-log someOutputLog";
		PeerOptions opts = PeerOptions.parse(line.split(" "));

		assertNull(opts.inLog);
		assertNotNull(opts.outLog);
		assertEquals("someOutputLog", opts.outLog.getName());
		assertFalse(opts.offsetGiven);
		assertNull(opts.offset);
	}

	@Test
	public void parse_argsWithInLogAndOutLog_logsParsed() {

		String line = "--read-log myInputLog -write-log someOutputLog";
		PeerOptions opts = PeerOptions.parse(line.split(" "));

		assertNotNull(opts.inLog);
		assertEquals("myInputLog", opts.inLog.getName());
		assertNotNull(opts.outLog);
		assertEquals("someOutputLog", opts.outLog.getName());
		assertFalse(opts.offsetGiven);
		assertNull(opts.offset);
	}

	@Test
	public void parse_argsWithLog_logParsed() {

		String line = "--log inputOutputLog";
		PeerOptions opts = PeerOptions.parse(line.split(" "));

		assertNotNull(opts.inLog);
		assertEquals("inputOutputLog", opts.inLog.getName());
		assertNotNull(opts.outLog);
		assertEquals("inputOutputLog", opts.outLog.getName());
		assertFalse(opts.offsetGiven);
		assertNull(opts.offset);
	}
}
