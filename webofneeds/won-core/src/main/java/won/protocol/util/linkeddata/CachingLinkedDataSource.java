/*
 * Copyright 2012  Research Studios Austria Forschungsges.m.b.H.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package won.protocol.util.linkeddata;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import won.protocol.rest.DatasetResponseWithStatusCodeAndHeaders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.EnumSet.noneOf;

/**
 * LinkedDataSource implementation that uses an ehcache for caching.
 */
@Qualifier("default")
public class CachingLinkedDataSource extends LinkedDataSourceBase implements LinkedDataSource, InitializingBean
{
  private static final String CACHE_NAME = "linkedDataCache";
  private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z";
  private static final int DEFAULT_EXPIRY_PERIOD = 600;
  private static final int DEFAULT_BYTE_ARRAY_SIZE = 500;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  @Autowired(required = true)
  private EhCacheCacheManager cacheManager;
  private Ehcache cache;
  private CrawlerCallback crawlerCallback = null;

  //In-memory dataset for caching linked data.

  /**
   * Removes the element associated with the
   * specified URI from the cache
   * @param resource
   */
  public void invalidate(URI resource) {
    assert resource != null : "resource must not be null";
    cache.remove(makeCacheKey(resource, null));
  }

  public void invalidate(URI resource, URI requesterWebID) {
    assert (resource != null && requesterWebID != null) : "resource and requester must not be null";
    cache.remove(makeCacheKey(resource, requesterWebID));
  }
  
  public void clear(){
    cache.removeAll();
  }

  public Dataset getDataForResource(URI resource, URI requesterWebID){

    assert resource != null : "resource must not be null";
    Element element = null;

    try {
        element = cache.get(makeCacheKey(resource, requesterWebID));
    }catch(CacheException e){
        //logging on warn level as not reporting errors here can make misconfiguration hard to detect
        logger.warn(String.format("Couldn't fetch resource %s", resource));
        logger.debug("Exception is:", e);
        return DatasetFactory.createGeneral();
    }
    LinkedDataCacheEntry linkedDataCacheEntry = null;
    if (element != null) {
      //cached element found
      Object cachedObject = element.getObjectValue();
      if (! (cachedObject instanceof LinkedDataCacheEntry)) {
        //wrong type - how did that happen?
        throw new IllegalStateException(
          new MessageFormat("The underlying linkedDataCache should only contain Datasets, but we got a {0} for URI {1}")
            .format(new Object[]{cachedObject.getClass(), resource}));
      }
      linkedDataCacheEntry = (LinkedDataCacheEntry) cachedObject;
    }
    return fetchOrUseCached(resource, requesterWebID, linkedDataCacheEntry).getDataset();

  }

  /**
   * This method respects the headers 'Expires', 'Cache-Control', and 'ETAG':
   * If a cached resource (indicated by a non-null linkedDataCacheEntry) is expired either according to
   * the expiry date or the cache-control header from the earlier request, the request will be made.
   * When the request is made and an ETAG value is known from an earlier request, it will be sent as the
   * 'If-None-Match' header value. In that case the server is expected to answer with status 304 (not modified) and
   * the cached response will be used, updating cache control information if the server chooses to send 'Expires' or
   * 'Cache-Control' headers.
   *
   * @param resource the URI of the resource to fetch
   * @param requesterWebID optional WebID URI to use for the request
   * @param linkedDataCacheEntry optional cache entry to use
   * @return
   */
  private DatasetResponseWithStatusCodeAndHeaders fetchOrUseCached(final URI resource, final URI
    requesterWebID, LinkedDataCacheEntry linkedDataCacheEntry) {

    //check
    // * if we have a cached result
    //  * if we can use it
    // * make request, possibly using ETAG
    //  * cache the new result if appropriate
    //  * if ETAG indicates not modified, return cached result but update caching info
    // * return result
    DatasetResponseWithStatusCodeAndHeaders responseData = null;
    Map<String, String> headers = new HashMap<>();

    if (linkedDataCacheEntry != null) {
      Date now = new Date();
      //before we can return a cached result, make a few checks to see if we
      //are allowed to do that:
      if (linkedDataCacheEntry.isExpiredAtDate(now)) {
        //cache item is expired. Remove from cache and fetch again
        cache.remove(makeCacheKey(resource, requesterWebID));
        logger.debug("cache item {} expired, fetching again.", resource);
        return fetchOnlyOnce(resource, requesterWebID, linkedDataCacheEntry, headers);
      }
      if (linkedDataCacheEntry.getCacheControlFlags().contains(CacheControlFlag.PRIVATE)){
        // in this case we assume that the response is not publicly visible, so it depends on the specified
        // requesterWebID. The check is performed by the server. We cannot return a cached response
        // immediately, but further down the line the ETAG based system can do that.
        logger.debug("cache item {} is Cache-Control:private, will return cached copy only after server checks ETAG, " +
                       "therefore sending request to server.", resource);
        return fetchOnlyOnce(resource, requesterWebID, linkedDataCacheEntry, headers);
      }
      logger.debug("returning cached version of {}", resource);
      //we can use the cached result directly
      return linkedDataCacheEntry.recreateResponse();
    }

    //nothing found in the cache, fetch the resource remotely
    logger.debug("Nothing found in cache for {}, fetching remotely", resource);
    responseData = fetchOnlyOnce(resource, requesterWebID, null, headers);

    //inform the crawler callback
    if (crawlerCallback != null){
      try {
        crawlerCallback.onDatasetCrawled(resource, responseData.getDataset());
      } catch (Exception e ){
        logger.info(String.format("error during callback execution for dataset %s", resource.toString()), e);
      }
    }
    return responseData;
  }

  //synchronziation for concurrent requests to the same resource
  private ConcurrentMap<String, CountDownLatch> countDownLatchMap = new ConcurrentHashMap<>(10);

  /**
   * We may run into fetching the same URI multiple times at once. Make sure we make only one http request
   * and use the response for every client.
   * @param resource
   * @param requesterWebID
   * @param linkedDataCacheEntry
   * @param headers
   * @return
   */
  private DatasetResponseWithStatusCodeAndHeaders fetchOnlyOnce(final URI resource, final URI requesterWebID, final LinkedDataCacheEntry linkedDataCacheEntry, final Map<String, String> headers) {
    String cacheKey = makeCacheKey(resource, requesterWebID);
    CountDownLatch latch = new CountDownLatch(1);
    CountDownLatch preExistingLatch = countDownLatchMap.putIfAbsent(cacheKey, latch);
    try {
      if (preExistingLatch != null) {
        logger.debug("resource " + cacheKey +" is being fetched in another thread, we wait for its result and use it " +
                       "if it turns out to be cacheable");
        //in this case, another thread is already fetching the URI. Wait.
        try {
          preExistingLatch.await();
        } catch (InterruptedException e) {
          logger.warn("interrupted while waiting for another thread to fetch '" + resource + "'");
        }
        //now, the other thread is done fetching the resource. It may not have been allowed to cache it, in which case
        //we have to fetch it again. We try:
        Element element = cache.get(cacheKey);
        if (element != null) {
          logger.debug("resource " + cacheKey +" turned out to be cacheable, using it");
          //ok, we'll recreate a response from the cache.
          //Caution: this is not a copy, it's the SAME dataset - so manipulating the result causes side-effects.
          LinkedDataCacheEntry entry = (LinkedDataCacheEntry) element.getObjectValue();
          return entry.recreateResponse();
        }
        logger.debug("resource " + cacheKey +" did not turn out to be cacheable - fetching it, too");
        //so the cache still doesn't have it. We think it's better to let every thread
        //fetch it for itself.
      }
      DatasetResponseWithStatusCodeAndHeaders datasetResponse = fetchAndCacheIfAppropriate(resource, requesterWebID,
                                                                                           linkedDataCacheEntry,
                                                                                           headers);

      return datasetResponse;
    } finally {
        //remove the latch from the map if it is in there
        countDownLatchMap.remove(cacheKey, latch);
        //wake up all threads that might now be waiting at our latch
        latch.countDown();
    }
  }

  private DatasetResponseWithStatusCodeAndHeaders fetchAndCacheIfAppropriate(
      final URI resource,
      final URI requesterWebID,
      final LinkedDataCacheEntry linkedDataCacheEntry,
      final Map<String, String> headers) {
    DatasetResponseWithStatusCodeAndHeaders responseData = fetchWithEtagValidation(resource,
                                                                                   requesterWebID, linkedDataCacheEntry, headers);
    Date expires = parseCacheControlMaxAgeValue(resource, responseData);
    if (expires == null) {
      expires = parseExpiresHeader(resource, responseData);
    }

    EnumSet<CacheControlFlag> cacheControlFlags = parseCacheControlHeaderFlags(resource, responseData);
    if (cacheControlFlags.contains(CacheControlFlag.NO_STORE) || cacheControlFlags.contains(CacheControlFlag.NO_CACHE)){
      //we are not allowed to cache the result
      //make sure it's not in the cache from a previous request
      cache.remove(makeCacheKey(resource, requesterWebID));
      logger.debug("Fetched {}. Will not be cached due to Cache-Control headers sent by server",
                   resource);
      return responseData;
    }

    Date responseDate = parseDateHeader(resource, responseData);
    if (responseDate != null && expires != null){
      //old way of saying don't cache: Date header >= Expires header
      if (responseDate.equals(expires) || responseDate.after(expires)) {
        //we are not allowed to cache the result
        //make sure it's not in the cache from a previous request
        logger.debug("Fetched {}. Will not be cached due to Expires/Date header combination sent by server", resource);
        cache.remove(makeCacheKey(resource, requesterWebID));
        return responseData;
      }
    }

    //if we don't get a new etag, see if we have a 304 code - then we can use th old etag
    String etag = responseData.getResponseHeaders().getFirst(HttpHeaders.ETAG);
    if (etag == null && responseData.getStatusCode() == HttpStatus.NOT_MODIFIED.value()
      && linkedDataCacheEntry != null){
      etag = linkedDataCacheEntry.getEtag();
    }

    //cache the result
    LinkedDataCacheEntry entry = new LinkedDataCacheEntry(etag, expires, writeDatasetToByteArray(responseData.getDataset()),
                                                          cacheControlFlags, responseData.getResponseHeaders(),
                                                          responseData.getStatusCode());
    this.cache.put(new Element(makeCacheKey(resource, requesterWebID), entry));
    logger.debug("Fetched and cached {} ", resource);
    return responseData;
  }


    private static Dataset readDatasetFromByteArray(byte[] datasetbytes) {
        Dataset dataset = DatasetFactory.create();
        RDFDataMgr.read(dataset, new ByteArrayInputStream(datasetbytes), Lang.NQUADS);
        return dataset;
    }

    private static byte[] writeDatasetToByteArray(Dataset dataset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(DEFAULT_BYTE_ARRAY_SIZE);
        RDFDataMgr.write(out, dataset, Lang.NQUADS);
        return out.toByteArray();
    }




  /**
   * Checks if the cached entry has an ETAG value set and uses the 'If-None-Match' header if this is the case.
   * If the server responds with 304 - NOT_MODIFIED, the cached dataset replaces the (empty) dataset coming from the
   * server in the DatasetResponseWithStatusCodeAndHeaders.
   *
   * @param resource
   * @param requesterWebID
   * @param linkedDataCacheEntry
   * @param headers
   * @return
   */
  private DatasetResponseWithStatusCodeAndHeaders fetchWithEtagValidation(
      final URI resource,
      final URI requesterWebID,
      final LinkedDataCacheEntry linkedDataCacheEntry,
      final Map<String, String> headers) {
    if (linkedDataCacheEntry == null || linkedDataCacheEntry.getEtag() == null){
      logger.debug("fetching from server without ETAG validation: {} ", resource);
      return fetch(resource, requesterWebID, headers);
    }
    //we already have an etag - use it for validating
      Map<String, String> myHeaders = headers != null ? headers : new HashMap<>();
    myHeaders.put(HttpHeaders.IF_NONE_MATCH, linkedDataCacheEntry.getEtag());
    logger.debug("fetching from server with ETAG validation: {} ", resource);
    DatasetResponseWithStatusCodeAndHeaders datasetResponse = fetch(resource, requesterWebID,
                                                                    myHeaders);
    if (datasetResponse.getStatusCode() == HttpStatus.NOT_MODIFIED.value()){
      //replace dataset in response with the cached dataset
      logger.debug("server said our ETAG is still valid, using cached dataset for URI {} ", resource);
      datasetResponse = new DatasetResponseWithStatusCodeAndHeaders(readDatasetFromByteArray(linkedDataCacheEntry
                                                                                           .getDataset()),
                                                                    datasetResponse
                                                                                           .getStatusCode(),
                                                                    datasetResponse
                                                                                           .getResponseHeaders());
    } else {
      logger.debug("server said our ETAG is not valid, not using cached result for URI {} ", resource);
      // We would like to remove the item from the cache immediately because it is now outdated. However, we cannot
      // remove the cached result from the cache here because we may have gotten any response from the
      // server (i.e. 1xx, 2xx, 3xx, 4xx, 5xx). However, if the ETAG isn't valid, we'll overwrite the cache entry down
      // the line or remove it if the server decides to forbid caching.
    }
    return datasetResponse;
  }


  /**
   * Performs the actual request via the linkedDataRestClient.
   * @param resource
   * @param requesterWebID
   * @param headers
   * @return
   */
  private DatasetResponseWithStatusCodeAndHeaders fetch(final URI resource, final URI
    requesterWebID, final Map<String, String> headers) {
    final DatasetResponseWithStatusCodeAndHeaders responseData;
    if (requesterWebID != null){
      logger.debug("fetching linked data for URI {} with WebID {}", resource, requesterWebID);
      responseData = linkedDataRestClient.readResourceDataWithHeaders(resource, requesterWebID, headers);
    } else {
      logger.debug("fetching linked data for URI {} without WebID", resource, requesterWebID);
      responseData = linkedDataRestClient.readResourceDataWithHeaders(resource, headers);
    }
    return responseData;
  }

  private Date parseExpiresHeader(final URI resource, final DatasetResponseWithStatusCodeAndHeaders responseData) {
    String expiresHeader = responseData.getResponseHeaders().getFirst(HttpHeaders.EXPIRES);
    if (expiresHeader == null) {
        return null;
    }
    expiresHeader = expiresHeader.trim();
    SimpleDateFormat format = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.ENGLISH);
    Date expires = null;
    try {
      expires = format.parse(expiresHeader);
    } catch (ParseException e) {
      //cannot parse expires header - use a default
      expires = addNSecondsToNow(DEFAULT_EXPIRY_PERIOD);
      //TODO: there seems to be a problem with the Expires header from the LinkedDataService
      logger.debug("could not parse 'Expires' header ' "
       + expiresHeader +"' obtained for '" + resource + "', using default expiry period of " + DEFAULT_EXPIRY_PERIOD
                    +" " +
                    "seconds");
    }
    return expires;
  }

  private Date addNSecondsToNow(int seconds) {
    final Date expires;Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());
    cal.add(Calendar.SECOND, seconds);
    expires = cal.getTime();
    return expires;
  }

  private Date parseDateHeader(final URI resource, final DatasetResponseWithStatusCodeAndHeaders
    responseData) {
    String dateHeader = responseData.getResponseHeaders().getFirst(HttpHeaders.DATE);
    if (dateHeader == null){
      return null;
    }
    SimpleDateFormat format = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.ENGLISH);
    Date date = null;
    try {
      date = format.parse(dateHeader);
    } catch (ParseException e) {
      //cannot parse expires header - use a default
      date = new Date();
      logger.warn("could not parse 'Date' header ' "
                    + dateHeader +"' obtained for '" + resource + "', using current date");
    }
    return date;
  }

  private EnumSet<CacheControlFlag> parseCacheControlHeaderFlags(final URI resource, final DatasetResponseWithStatusCodeAndHeaders responseData) {
    String cacheControlHeaderValue = responseData.getResponseHeaders().getFirst(HttpHeaders.CACHE_CONTROL);
    EnumSet<CacheControlFlag> cacheControlFlags = EnumSet.noneOf(CacheControlFlag.class);
    if (cacheControlHeaderValue == null) return cacheControlFlags;
    String[] values = cacheControlHeaderValue.split(",");
    for (String value : values){
      CacheControlFlag flag = CacheControlFlag.forName(value.trim());
      if (flag != null) {
        cacheControlFlags.add(flag);
      }
    }
    return cacheControlFlags;
  }

  private Date parseCacheControlMaxAgeValue(final URI resource, final DatasetResponseWithStatusCodeAndHeaders responseData) {
    String cacheControlHeaderValue = responseData.getResponseHeaders().getFirst(HttpHeaders.CACHE_CONTROL);
    if (cacheControlHeaderValue == null) return null;
    Pattern maxagePattern = Pattern.compile("[^\\s,]*max-age\\s*=\\s*(\\d+)[$\\s,]");
    Matcher m = maxagePattern.matcher(cacheControlHeaderValue);
    if (!m.find()) return null;
    String maxAgeValueString = m.group(1);
    int maxAgeInt = 3600;
    try {
      maxAgeInt = Integer.parseInt(maxAgeValueString);
    } catch (NumberFormatException e){
      logger.warn("could not parse 'Expires' header ' "
                    + cacheControlHeaderValue +"' obtained for '" + resource + "' using default expiry period of 1 hour",e );
    }
    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());
    cal.add(Calendar.SECOND, maxAgeInt);
    return cal.getTime();
  }

  @Override
  public Dataset getDataForResource(final URI resource) {
    return getDataForResource(resource, null);
  }

  @Override
  public void afterPropertiesSet() throws Exception
  {
    Ehcache baseCache = cacheManager.getCacheManager().getCache(CACHE_NAME);
    if (baseCache == null) {
      throw new IllegalArgumentException(String.format("could not find a cache with name '%s' in ehcache config",
                                                    CACHE_NAME));
    }
    //this.cache = new SelfPopulatingCache(baseCache, new LinkedDataCacheEntryFactory());
    this.cache = baseCache;
  }

  public void setCacheManager(final EhCacheCacheManager cacheManager)
  {
    this.cacheManager = cacheManager;
  }




  @Autowired(required = false)
  public void setCrawlerCallback(final CrawlerCallback crawlerCallback) {
    this.crawlerCallback = crawlerCallback;
  }




  public static enum CacheControlFlag
  {
    PUBLIC("public"),
    PRIVATE("private"),
    NO_CACHE("no-cache"),
    NO_STORE("no-store"),
    MUST_REVALIDATE("must-revalidate")
    ;

    private String name;

    CacheControlFlag(final String name) {
      this.name = name;
    }

    public static CacheControlFlag forName(String name){
      switch (name){
        case "public": return PUBLIC;
        case "private": return PRIVATE;
        case "no-cache": return NO_CACHE;
        case "no-store": return NO_STORE;
        case "must-revalidate": return MUST_REVALIDATE;
      }
      return null;
    }

    public String getName() {
      return name;
    }
  }




public static class LinkedDataCacheEntry
  {
    private String etag = null;
    private Date expires = null;
    private byte[] dataset = null;
    private EnumSet<CacheControlFlag> cacheControlFlags = noneOf(CacheControlFlag.class);
    private HttpHeaders headers;
    private int statusCode;


    public LinkedDataCacheEntry(final String etag, final Date expires, final byte[] dataset, final EnumSet<CacheControlFlag> cacheControlFlags, final HttpHeaders headers, final int statusCode) {
      this.etag = etag;
      this.expires = expires;
      this.dataset = dataset;
      this.cacheControlFlags = cacheControlFlags != null ? cacheControlFlags : noneOf(CacheControlFlag.class);
      this.headers = headers;
      this.statusCode = statusCode;
    }

    public DatasetResponseWithStatusCodeAndHeaders recreateResponse(){
      return new DatasetResponseWithStatusCodeAndHeaders(readDatasetFromByteArray(dataset), statusCode, headers);
    }

    public String getEtag() {
      return etag;
    }

    public byte[] getDataset() {
      return dataset;
    }

    public Date getExpires() {
      return expires;
    }

    public EnumSet<CacheControlFlag> getCacheControlFlags() {
      return cacheControlFlags;
    }

    /**
     * Checks if the cache item is expired at the given date.
     * If the cache item has no expiry date set, the method returns false for any given date.
     * @param when
     * @return
     */
    public boolean isExpiredAtDate(final Date when) {
      if (expires == null) return false;
      return expires.before(when);
    }
  }

  private String makeCacheKey(URI resource, URI requesterWebID){
    //using spaces in the null placeholder to make it impossible to inject a requesterWebID URI that is equal to the
    //null place holder (because an URI can't have spaces).
    return resource.toString() + (requesterWebID == null ? " (no Web ID)":requesterWebID.toString());
  }
}
