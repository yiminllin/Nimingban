/*
 * Copyright 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.nimingban.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.hippo.app.ProgressDialogBuilder;
import com.hippo.io.UriInputStreamPipe;
import com.hippo.nimingban.Emoji;
import com.hippo.nimingban.NMBAppConfig;
import com.hippo.nimingban.NMBApplication;
import com.hippo.nimingban.R;
import com.hippo.nimingban.client.NMBClient;
import com.hippo.nimingban.client.NMBRequest;
import com.hippo.nimingban.client.ac.ACUrl;
import com.hippo.nimingban.client.ac.data.ACPostStruct;
import com.hippo.nimingban.client.ac.data.ACReplyStruct;
import com.hippo.nimingban.client.data.Site;
import com.hippo.nimingban.network.SimpleCookieStore;
import com.hippo.nimingban.util.BitmapUtils;
import com.hippo.nimingban.util.DB;
import com.hippo.nimingban.util.ReadableTime;
import com.hippo.rippleold.RippleSalon;
import com.hippo.util.ExceptionUtils;
import com.hippo.widget.recyclerview.EasyRecyclerView;
import com.hippo.widget.recyclerview.SimpleHolder;
import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.LayoutUtils;
import com.hippo.yorozuya.ResourcesUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

// TODO add edit text for name, title and so on
public final class TypeSendActivity extends AbsActivity implements View.OnClickListener {

    private static final String TAG = TypeSendActivity.class.getSimpleName();

    public static final String ACTION_REPLY = "com.hippo.nimingban.ui.TypeSendActivity.action.REPLY";
    public static final String ACTION_CREATE_POST = "com.hippo.nimingban.ui.TypeSendActivity.action.CREATE_POST";
    public static final String ACTION_REPORT = "com.hippo.nimingban.ui.TypeSendActivity.action.REPORT";

    public static final String KEY_SITE = "site";
    public static final String KEY_ID = "id";
    public static final String KEY_TEXT = "text";

    public static final int REQUEST_CODE_SELECT_IMAGE = 0;
    public static final int REQUEST_CODE_DRAFT = 1;
    public static final int REQUEST_CODE_DOODLE = 2;
    public static final int REQUEST_CODE_CAMERA = 3;

    private Method mMethod;

    private NMBClient mNMBClient;

    private EditText mEditText;
    private View mEmoji;
    private View mImage;
    private View mDraw;
    private View mDraft;
    private View mSend;
    private View mImagePreview;
    private ImageView mPreview;
    private View mDelete;

    private Site mSite;
    private String mId;
    private String mPresetText;

    private Uri mSeletedImageUri;
    private String mSeletedImageType;
    private Bitmap mSeletedImageBitmap;

    private Uri mCameraImageUri;

    private Dialog mProgressDialog;
    private NMBRequest mNMBRequest;

    private enum Method {
        Reply,
        CreatePost
    }

    // false for error
    private boolean handlerIntent(Intent intent) {
        if (intent == null) {
            return false;
        }

        boolean report = false;

        String action = intent.getAction();
        if (ACTION_REPLY.equals(action)) {
            mMethod = Method.Reply;
            setTitle(R.string.reply);
        } else if (ACTION_CREATE_POST.equals(action)) {
            mMethod = Method.CreatePost;
            setTitle(R.string.create_post);
        } else if (ACTION_REPORT.equals(action)) {
            mMethod = Method.CreatePost;
            setTitle(R.string.report);
            report = true;
        }

        if (mMethod != null) {
            int site = intent.getIntExtra(KEY_SITE, -1);
            String id = intent.getStringExtra(KEY_ID);
            mPresetText = intent.getStringExtra(KEY_TEXT);
            if (Site.isValid(site) && id != null) {
                mSite = Site.fromId(site);
                mId = id;

                if (report) {
                    Toast.makeText(this, R.string.report_tip, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        }

        return false;
    }

    @Override
    protected int getLightThemeResId() {
        return R.style.AppTheme;
    }

    @Override
    protected int getDarkThemeResId() {
        return R.style.AppTheme_Dark;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!handlerIntent(getIntent())) {
            finish();
            return;
        }

        mNMBClient = NMBApplication.getNMBClient(this);

        setContentView(R.layout.activity_reply);

        mEditText = (EditText) findViewById(R.id.edit_text);
        mEmoji = findViewById(R.id.emoji);
        mImage = findViewById(R.id.image);
        mDraw = findViewById(R.id.draw);
        mDraft = findViewById(R.id.draft);
        mSend = findViewById(R.id.send);
        mImagePreview = findViewById(R.id.image_preview);
        mPreview = (ImageView) mImagePreview.findViewById(R.id.preview);
        mDelete = mImagePreview.findViewById(R.id.delete);

        RippleSalon.addRipple(mEmoji, true);
        RippleSalon.addRipple(mImage, true);
        RippleSalon.addRipple(mDraw, true);
        RippleSalon.addRipple(mDraft, true);
        RippleSalon.addRipple(mSend, true);

        mEmoji.setOnClickListener(this);
        mImage.setOnClickListener(this);
        mDraw.setOnClickListener(this);
        mDraft.setOnClickListener(this);
        mSend.setOnClickListener(this);
        mDelete.setOnClickListener(this);

        if (!TextUtils.isEmpty(mPresetText)) {
            mEditText.append(mPresetText);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        clearImagePreview();

        if (mNMBRequest != null) {
            mNMBRequest.cancel();
            mNMBRequest = null;
        }
    }

    @Override
    public void onBackPressed() {
        final String text = mEditText.getText().toString().trim();
        if (!text.isEmpty()) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        DB.addDraft(mEditText.getText().toString());
                        finish();
                    } else if (which == DialogInterface.BUTTON_NEUTRAL) {
                        finish();
                    }
                }
            };

            new AlertDialog.Builder(this).setMessage(R.string.save_text_draft)
                    .setPositiveButton(R.string.save, listener)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.dont_save, listener)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    private boolean hasACCookies() {
        SimpleCookieStore cookieStore = NMBApplication.getSimpleCookieStore(this);
        URL url;
        try {
            url = new URL(ACUrl.HOST);
        } catch (MalformedURLException e) {
            // WTF ?
            return true;
        }
        return cookieStore.contain(url, "userId");
    }

    private void doAction() {
        if (mMethod == Method.Reply) {
            doReply();
        } else if (mMethod == Method.CreatePost) {
            doCreatePost();
        } else {
            Log.d(TAG, "WTF?, an unknown method in TypeSendActivity " + mMethod);
        }
    }

    private void showProgressDialog(int resId) {
        if (mProgressDialog != null) {
            return;
        }

        DialogInterface.OnClickListener clicklistener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mNMBRequest != null) {
                    mNMBRequest.cancel();
                    mNMBRequest = null;
                }
            }
        };

        DialogInterface.OnDismissListener dismissListener = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mProgressDialog = null;
            }
        };

        mProgressDialog = new ProgressDialogBuilder(this)
                .setTitle(R.string.please_wait)
                .setMessage(resId)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, clicklistener)
                .setOnDismissListener(dismissListener)
                .show();
    }

    private void doReply() {
        showProgressDialog(R.string.replying);

        ACReplyStruct struct = new ACReplyStruct();
        struct.name = null;
        struct.email = null;
        struct.title = null;
        struct.content = mEditText.getText().toString();
        struct.resto = mId;
        struct.image = mSeletedImageUri != null ? new UriInputStreamPipe(getApplicationContext(), mSeletedImageUri) : null;
        struct.imageType = mSeletedImageType;

        NMBRequest request = new NMBRequest();
        mNMBRequest = request;
        request.setSite(mSite);
        request.setMethod(NMBClient.METHOD_REPLY);
        request.setArgs(struct);
        request.setCallback(new ActionListener());
        mNMBClient.execute(request);
    }

    private void doCreatePost() {
        showProgressDialog(R.string.creating_post);

        ACPostStruct struct = new ACPostStruct();
        struct.name = null;
        struct.email = null;
        struct.title = null;
        struct.content = mEditText.getText().toString();
        struct.fid = mId;
        struct.image = mSeletedImageUri != null ? new UriInputStreamPipe(getApplicationContext(), mSeletedImageUri) : null;
        struct.imageType = mSeletedImageType;

        NMBRequest request = new NMBRequest();
        mNMBRequest = request;
        request.setSite(mSite);
        request.setMethod(NMBClient.METHOD_CREATE_POST);
        request.setArgs(struct);
        request.setCallback(new ActionListener());
        mNMBClient.execute(request);
    }

    private void getCookies() {
        showProgressDialog(R.string.getting_cookies);

        NMBRequest request = new NMBRequest();
        mNMBRequest = request;
        request.setSite(mSite);
        request.setMethod(NMBClient.METHOD_GET_COOKIE);
        request.setCallback(new GetCookieListener());
        mNMBClient.execute(request);
    }

    private void tryGettingCookies() {
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        getCookies();
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        doAction();
                        break;
                }
            }
        };

        new AlertDialog.Builder(this).setTitle(R.string.no_cookies)
                .setMessage(R.string.no_cookies_ac)
                .setPositiveButton(android.R.string.ok, listener)
                .setNegativeButton(android.R.string.cancel, listener)
                .setNeutralButton(R.string.i_dont_care, listener).show();
    }

    private class EmojiDialogHelper implements EasyRecyclerView.OnItemClickListener,
            DialogInterface.OnDismissListener {

        private Dialog mDialog;
        private View mView;

        private EmojiDialogHelper() {
            @SuppressLint("InflateParams")
            EasyRecyclerView recyclerView = (EasyRecyclerView) TypeSendActivity.this
                    .getLayoutInflater().inflate(R.layout.dialog_emoji, null);
            recyclerView.setAdapter(new EmojiAdapter());
            recyclerView.setLayoutManager(new StaggeredGridLayoutManager(
                    3, StaggeredGridLayoutManager.VERTICAL));// TODO adjust by view width
            recyclerView.setSelector(RippleSalon.generateRippleDrawable(
                    ResourcesUtils.getAttrBoolean(TypeSendActivity.this, R.attr.dark)));
            recyclerView.setOnItemClickListener(this);
            mView = recyclerView;
        }

        public View getView() {
            return mView;
        }

        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        @Override
        public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
            if (mDialog != null) {
                EditText editText = mEditText;
                String emoji = Emoji.EMOJI_VALUE[position];
                int start = Math.max(editText.getSelectionStart(), 0);
                int end = Math.max(editText.getSelectionEnd(), 0);
                editText.getText().replace(Math.min(start, end), Math.max(start, end),
                        emoji, 0, emoji.length());
                mDialog.dismiss();
                mDialog = null;
            }
            return true;
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            mDialog = null;
        }


        private class EmojiAdapter extends RecyclerView.Adapter<SimpleHolder> {

            @SuppressLint("InflateParams")
            @Override
            public SimpleHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return new SimpleHolder(TypeSendActivity.this
                        .getLayoutInflater().inflate(R.layout.item_emoji, null));
            }

            @Override
            public void onBindViewHolder(SimpleHolder holder, int position) {
                ((TextView) holder.itemView).setText(Emoji.EMOJI_NAME[position]);
            }

            @Override
            public int getItemCount() {
                return Emoji.COUNT;
            }
        }
    }

    private void showEmojiDialog() {
        EmojiDialogHelper helper = new EmojiDialogHelper();
        Dialog dialog = new AlertDialog.Builder(this)
                .setView(helper.getView())
                .setOnDismissListener(helper)
                .create();
        helper.setDialog(dialog);
        dialog.show();
    }

    private void showImageDialog() {
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent;
                switch (which) {
                    case 0:
                        intent = new Intent();
                        intent.setType("image/*");
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        startActivityForResult(Intent.createChooser(intent,
                                getString(R.string.select_picture)), REQUEST_CODE_SELECT_IMAGE);
                        break;
                    case 1:
                        File dir = NMBAppConfig.getPhotoDir();
                        if (dir == null)
                            break;
                        File temp = new File(dir, ReadableTime.getFilenamableTime(System.currentTimeMillis()) + ".jpg");
                        mCameraImageUri = Uri.fromFile(temp);
                        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCameraImageUri);
                        startActivityForResult(cameraIntent, REQUEST_CODE_CAMERA);
                        break;
                }
            }
        };

        new AlertDialog.Builder(this).setItems(R.array.image_dialog, listener).show();
    }

    @Override
    public void onClick(View v) {
        if (mSend == v) {
            if (mProgressDialog != null || mNMBRequest != null) {
                return;
            }

            if (hasACCookies()) {
                doAction();
            } else {
                tryGettingCookies();
            }
        } else if (mEmoji == v) {
            showEmojiDialog();
        } else if (mImage == v) {
            showImageDialog();
        } else if (mDraft == v) {
            Intent intent = new Intent(TypeSendActivity.this, DraftActivity.class);
            startActivityForResult(intent, REQUEST_CODE_DRAFT);
        } else if (mDraw == v) {
            Intent intent = new Intent(TypeSendActivity.this, DoodleActivity.class);
            startActivityForResult(intent, REQUEST_CODE_DOODLE);
        } else if (mDelete == v) {
            clearImagePreview();
        }
    }

    private void clearImagePreview() {
        if (mSeletedImageBitmap != null) {
            mSeletedImageBitmap.recycle();
        }
        mSeletedImageUri = null;
        mSeletedImageType = null;
        mSeletedImageBitmap = null;

        mPreview.setImageDrawable(null);
        mImagePreview.setVisibility(View.GONE);

        ViewGroup.LayoutParams lp = mEditText.getLayoutParams();
        if (lp == null) {
            lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        } else{
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        mEditText.setLayoutParams(lp);
    }

    private void setImagePreview(Uri uri, String type, Bitmap bitmap) {
        if (mSeletedImageBitmap != null) {
            mSeletedImageBitmap.recycle();
        }
        mSeletedImageUri = uri;
        mSeletedImageType = type;
        mSeletedImageBitmap = bitmap;

        mPreview.setImageBitmap(bitmap);
        mImagePreview.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams lp = mEditText.getLayoutParams();
        if (lp == null) {
            lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        } else{
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        mEditText.setLayoutParams(lp);
    }

    // TODO do not do it in UI thread
    private boolean handleSelectedImageUri(Uri uri) {
        if (uri == null) {
            return false;
        }

        ContentResolver resolver = getContentResolver();
        String type = resolver.getType(uri);
        if (type == null) {
            type =  MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    FileUtils.getExtensionFromFilename(uri.toString()));
        }

        int maxSize = LayoutUtils.dp2pix(this, 256);
        Bitmap bitmap = BitmapUtils.decodeStream(new UriInputStreamPipe(this, uri), maxSize, maxSize);
        if (bitmap != null) {
            setImagePreview(uri, type, bitmap);
            return true;
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_CODE_SELECT_IMAGE || requestCode == REQUEST_CODE_DOODLE) && resultCode == RESULT_OK) {
            handleSelectedImageUri(data.getData());
        } else if (requestCode == REQUEST_CODE_CAMERA && resultCode == RESULT_OK) {
            handleSelectedImageUri(mCameraImageUri);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private class ActionListener implements NMBClient.Callback<Void> {
        @Override
        public void onSuccess(Void result) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            mNMBRequest = null;

            Toast.makeText(TypeSendActivity.this, mMethod == Method.Reply ? R.string.reply_successfully :
                    R.string.create_post_successfully, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }

        @Override
        public void onFailure(Exception e) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            mNMBRequest = null;

            Toast.makeText(TypeSendActivity.this, getString(mMethod == Method.Reply ? R.string.reply_failed :
                    R.string.create_post_failed) +
                    ExceptionUtils.getReadableString(TypeSendActivity.this, e), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancelled() {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            mNMBRequest = null;

            Log.d(TAG, "ActionListener onCancel");
        }
    }

    private class GetCookieListener implements NMBClient.Callback<Boolean> {
        @Override
        public void onSuccess(Boolean result) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            mNMBRequest = null;

            Toast.makeText(TypeSendActivity.this, R.string.got_cookies, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onFailure(Exception e) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            mNMBRequest = null;

            Toast.makeText(TypeSendActivity.this, getString(R.string.cant_get_cookies) +
                    ExceptionUtils.getReadableString(TypeSendActivity.this, e), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancelled() {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            mNMBRequest = null;

            Log.d(TAG, "GetCookieListener onCancel");
        }
    }
}
