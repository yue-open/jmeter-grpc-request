package vn.zalopay.benchmark;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.ThreadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.zalopay.benchmark.constant.GrpcSamplerConstant;
import vn.zalopay.benchmark.core.ClientCaller;
import vn.zalopay.benchmark.core.config.GrpcRequestConfig;
import vn.zalopay.benchmark.core.specification.GrpcResponse;
import vn.zalopay.benchmark.util.ExceptionUtils;

import java.nio.charset.StandardCharsets;

public class GRPCSampler extends AbstractSampler implements ThreadListener {

    private static final Logger log = LoggerFactory.getLogger(GRPCSampler.class);
    private static final long serialVersionUID = 232L;

    public static final String METADATA = "GRPCSampler.metadata";
    public static final String LIB_FOLDER = "GRPCSampler.libFolder";
    public static final String PROTO_FOLDER = "GRPCSampler.protoFolder";
    public static final String HOST = "GRPCSampler.host";
    public static final String PORT = "GRPCSampler.port";
    public static final String FULL_METHOD = "GRPCSampler.fullMethod";
    public static final String REQUEST_JSON = "GRPCSampler.requestJson";
    public static final String DEADLINE = "GRPCSampler.deadline";
    public static final String TLS = "GRPCSampler.tls";
    public static final String TLS_DISABLE_VERIFICATION = "GRPCSampler.tlsDisableVerification";
    public static final String CHANNEL_SHUTDOWN_AWAIT_TIME = "GRPCSampler.channelAwaitTermination";
    private transient ClientCaller clientCaller;
    private GrpcRequestConfig grpcRequestConfig;

    public GRPCSampler() {
        super();
        trace("init GRPCSampler");
    }

    /**
     * @return a string for the sampleResult Title
     */
    private String getTitle() {
        return this.getName();
    }

    private void trace(String s) {
        String threadName = Thread.currentThread().getName();
        log.debug("{} ({}) {} {}", threadName, getTitle(), s, this);
    }

    private void initGrpcConfigRequest() {
        if (grpcRequestConfig == null) {
            grpcRequestConfig = new GrpcRequestConfig(
                    getHostPort(),
                    getProtoFolder(),
                    getLibFolder(),
                    getFullMethod(),
                    isTls(),
                    isTlsDisableVerification(),
                    getChannelShutdownAwaitTime()
            );
        }
    }

    private void initGrpcClient() {
        if (clientCaller == null) {
            clientCaller = new ClientCaller(grpcRequestConfig);
        }
    }

    @Override
    public SampleResult sample(Entry ignored) {
        SampleResult sampleResult = new SampleResult();

        // Intercepts exceptions before GRPC requests are initiated
        try {
            sampleResult.setSampleLabel(getName());
            initGrpcConfigRequest();
            initGrpcClient();
            String grpcRequest = clientCaller.buildRequestAndMetadata(getRequestJson(), getMetadata());
            sampleResult.setSamplerData(grpcRequest);
            sampleResult.setRequestHeaders(clientCaller.getMetadataString());
            sampleResult.sampleStart();
        } catch (Exception e) {
            sampleResult.setSuccessful(false);
            sampleResult.setResponseCode(" 400");
            sampleResult.setDataType(SampleResult.TEXT);
            sampleResult.setResponseMessage(GrpcSamplerConstant.CLIENT_EXCEPTION_MSG);
            sampleResult.setResponseData(ExceptionUtils.getPrintExceptionToStr(e, null), "UTF-8");
            return sampleResult;
        }

        // Initiate a GRPC request
        GrpcResponse grpcResponse = clientCaller.call(getDeadline());
        sampleResult.sampleEnd();
        sampleResult.setDataType(SampleResult.TEXT);
        if (grpcResponse.isSuccess()) {
            sampleResult.setSuccessful(true);
            sampleResult.setResponseCodeOK();
            sampleResult.setResponseMessage(" success");
            sampleResult.setResponseData(grpcResponse.getGrpcMessageString().getBytes(StandardCharsets.UTF_8));
        } else {
            Throwable throwable = grpcResponse.getThrowable();
            sampleResult.setSuccessful(false);
            sampleResult.setResponseCode(" 500");
            String responseMessage = " ";
            String responseData = "";
            if (throwable instanceof StatusRuntimeException) {
                Status status = ((StatusRuntimeException) throwable).getStatus();
                Status.Code code = status.getCode();
                responseMessage += code.value() + " " + code.name();
                responseData = status.getDescription();
            } else {
                responseMessage += ExceptionUtils.getPrintExceptionToStr(throwable, 0);
                responseData = responseMessage;
            }

            sampleResult.setResponseMessage(responseMessage);
            sampleResult.setResponseData(responseData, "UTF-8");
        }

        return sampleResult;
    }

    @Override
    public void clear() {
        super.clear();
    }

    @Override
    public void threadStarted() {
        log.debug("\ttestStarted: {}", whoAmI());
    }

    @Override
    public void threadFinished() {
        log.debug("\ttestEnded: {}", whoAmI());
        if (clientCaller != null) {
            clientCaller.shutdownNettyChannel();
            clientCaller = null;
        }
        // clear state of grpc config for rerun with new config in GUI mode
        if (grpcRequestConfig != null) {
            grpcRequestConfig = null;
        }
    }

    private String whoAmI() {
        return Thread.currentThread().getName() +
                "@" +
                Integer.toHexString(hashCode()) +
                "-" +
                getName();
    }

    /**
     * GETTER AND SETTER
     */
    public String getMetadata() {
        return getPropertyAsString(METADATA);
    }

    public void setMetadata(String metadata) {
        setProperty(METADATA, metadata);
    }

    public String getLibFolder() {
        return getPropertyAsString(LIB_FOLDER);
    }

    public void setLibFolder(String libFolder) {
        setProperty(LIB_FOLDER, libFolder);
    }

    public String getProtoFolder() {
        return getPropertyAsString(PROTO_FOLDER);
    }

    public void setProtoFolder(String protoFolder) {
        setProperty(PROTO_FOLDER, protoFolder);
    }

    public String getFullMethod() {
        return getPropertyAsString(FULL_METHOD);
    }

    public void setFullMethod(String fullMethod) {
        setProperty(FULL_METHOD, fullMethod);
    }

    public String getRequestJson() {
        return getPropertyAsString(REQUEST_JSON);
    }

    public void setRequestJson(String requestJson) {
        setProperty(REQUEST_JSON, requestJson);
    }

    public String getDeadline() {
        return getPropertyAsString(DEADLINE);
    }

    public void setDeadline(String deadline) {
        setProperty(DEADLINE, deadline);
    }

    public boolean isTls() {
        return getPropertyAsBoolean(TLS);
    }

    public void setTls(boolean tls) {
        setProperty(TLS, tls);
    }

    public boolean isTlsDisableVerification() {
        return getPropertyAsBoolean(TLS_DISABLE_VERIFICATION);
    }

    public void setTlsDisableVerification(boolean tlsDisableVerification) {
        setProperty(TLS_DISABLE_VERIFICATION, tlsDisableVerification);
    }

    public String getHost() {
        return getPropertyAsString(HOST);
    }

    public void setHost(String host) {
        setProperty(HOST, host);
    }

    public String getPort() {
        return getPropertyAsString(PORT);
    }

    public void setPort(String port) {
        setProperty(PORT, port);
    }

    public String getChannelShutdownAwaitTime() {
        return getPropertyAsString(CHANNEL_SHUTDOWN_AWAIT_TIME, "1000");
    }

    public void setChannelShutdownAwaitTime(String awaitShutdownTime) {
        setProperty(CHANNEL_SHUTDOWN_AWAIT_TIME, awaitShutdownTime);
    }

    private String getHostPort() {
        return getHost() + ":" + getPort();
    }

}
