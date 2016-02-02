package com.anthonymandra.sample;

import android.os.Bundle;
import android.support.annotation.Nullable;

public class UnmanagedSampleActivity extends BaseActivity
{
	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setStoragePermissionRequestEnabled(false);
	}
}
