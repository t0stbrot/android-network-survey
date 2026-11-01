package com.craxiom.networksurvey.logging.db.uploader.t0st;

import static com.google.common.net.HttpHeaders.USER_AGENT;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.logging.db.uploader.RequestResult;
import com.craxiom.networksurvey.logging.db.uploader.UploadEndpoints;
import com.craxiom.networksurvey.logging.db.uploader.UploadResponses;
import com.craxiom.networksurvey.logging.db.uploader.UploadResult;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import timber.log.Timber;

public interface T0stUploadClient
{
    @Multipart
    @POST("/measure/uploadCsv")
    Call<ResponseBody> uploadToT0st(
            @Part("key") RequestBody name,
            @Part("app") RequestBody appId,
            @Part MultipartBody.Part dataFile
    );

    static T0stUploadClient getInstance()
    {
        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new UserAgentInterceptor())
                .build();

        return new Retrofit.Builder()
                .baseUrl(UploadEndpoints.getT0stBaseUrl())
                .client(client)
                .build()
                .create(T0stUploadClient.class);
    }

    class UserAgentInterceptor implements Interceptor
    {
        @NonNull
        @Override
        public Response intercept(Chain chain) throws IOException
        {
            Request originalRequest = chain.request();
            Request requestWithUserAgent = originalRequest.newBuilder()
                    .header(USER_AGENT, "NetworkSurvey/" + BuildConfig.VERSION_NAME)
                    .build();
            return chain.proceed(requestWithUserAgent);
        }
    }

    /** The body @t0stbrot.net answers a successful upload with. */
    String T0ST_OK_BODY = "0,OK";

    /**
     * Takes the result of an upload HTTP call to @t0stbrot.net and returns the corresponding RequestResult.
     *
     * @param body The response body, or for a non-2xx response the error body.
     */
    static RequestResult handleT0stResponse(int code, @Nullable ResponseBody body)
    {
        String bodyString = "";
        try
        {
            if (body != null)
            {
                bodyString = body.string();
            }
        } catch (IOException e)
        {
            Timber.e(e, "Error reading response body from OCID");
        }
        return handleT0stResponse(code, bodyString);
    }

    static RequestResult handleT0stResponse(int code, @NonNull String body)
    {
        final String bodyString = body.trim();
        if (code == 200 && T0ST_OK_BODY.equalsIgnoreCase(bodyString))
        {
            return RequestResult.Success;
        } else
        {
            Timber.w("Received status code %s when uploading records to @t0stbrot.net; body=%s", code, UploadResponses.logSnippet(bodyString));
        }

        if (code >= 500 && code <= 599)
        {
            return RequestResult.ServerError;
        }
        if (code == 400)
        {
            return RequestResult.ConfigurationError;
        }
        return RequestResult.ConnectionError;
    }

    static UploadResult mapRequestResultToUploadResult(RequestResult response)
    {
        return switch (response)
        {
            case ConfigurationError -> UploadResult.InvalidData;
            case ServerError -> UploadResult.ServerError;
            case ConnectionError -> UploadResult.ConnectionError;
            case LimitExceeded -> UploadResult.LimitExceeded;
            case Failure -> UploadResult.Failure;
            case Success ->
            {
                Timber.d("Upload successful.");
                yield UploadResult.Success;
            }
            default -> throw new UnsupportedOperationException(
                    String.format("Unsupported upload result: %s", response)
            );
        };
    }
}
