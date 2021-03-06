package com.github.hakko.musiccabinet.ws.lastfm;

import java.util.List;

import junit.framework.Assert;

import org.apache.http.NameValuePair;
import org.junit.Test;
import org.mockito.Mockito;

import com.github.hakko.musiccabinet.domain.model.library.WebserviceInvocation;
import com.github.hakko.musiccabinet.exception.ApplicationException;
import com.github.hakko.musiccabinet.service.lastfm.WebserviceHistoryService;

public class TagInfoClientTest extends AbstractWSImplementationTest {

	@Test
	public void validateParameters() throws ApplicationException {

		final String method = TagInfoClient.METHOD;
		final String tagName = "disco";
		
		new TagInfoClient() {
			@Override
			protected WSResponse executeWSRequest(WebserviceInvocation wi,
					List<NameValuePair> params) throws ApplicationException {
				
				Assert.assertNull(wi);
				
				assertHasParameter(params, PARAM_METHOD, method);
				assertHasParameter(params, PARAM_TAG, tagName);
				
				return null;
			}
			
			@Override
			protected WebserviceHistoryService getHistoryService() {
				return Mockito.mock(WebserviceHistoryService.class);
			}

		}.getTagInfo(tagName);
	}
	
}