package com.auknowlog.backend.source.service;

import java.net.URI;

record FetchedSource(URI finalUri, byte[] body, String contentType, String suggestedName) {
}
