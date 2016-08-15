package com.onehilltech.concurrent;

import java.util.concurrent.Executor;

abstract class TaskManager <T extends Object>
    implements Runnable
{
  private boolean isCancelled_ = false;
  private Throwable failure_;

  protected abstract void onRun ();

  protected abstract boolean isDone ();

  protected T result_;

  protected CompletionCallback completionCallback_;
  protected Executor executor_;

  protected class TaskCompletionCallback extends CompletionCallback
  {
    protected final Task task_;

    public TaskCompletionCallback (Task task)
    {
      this.task_ = task;
    }

    @Override
    protected void onCancel ()
    {
      TaskManager.this.cancel ();
    }

    @Override
    protected void onFail (Throwable e)
    {
      TaskManager.this.fail (e);
    }

    @Override
    protected void onComplete (Object result)
    {
      onTaskComplete (this.task_, result);
    }
  }

  protected TaskManager (Executor executor, CompletionCallback completionCallback)
  {
    this.executor_ = executor;
    this.completionCallback_ = completionCallback;
  }

  public void cancel ()
  {
    if (this.isCancelled_)
      return;

    this.isCancelled_ = true;
  }

  /**
   * Handle completion of a task.
   *
   * @param task
   * @param result
   */
  protected abstract void onTaskComplete (Task task, Object result);

  @Override
  public void run ()
  {
    if (this.failure_ != null)
    {
      this.completionCallback_.onFail (this.failure_);
    }
    else if (this.isCancelled_)
    {
      this.completionCallback_.onCancel ();
    }
    else if (this.isDone ())
    {
      this.completionCallback_.onComplete (this.result_);
    }
    else
    {
      try
      {
        this.onRun ();
      }
      catch (Exception e)
      {
        this.fail (e);
      }
    }
  }

  /**
   * Notify the task manager is has failed.
   *
   * @param e
   */
  protected synchronized void fail (Throwable e)
  {
    if (this.failure_ != null)
      return;

    this.failure_ = e;

    this.executor_.execute (new Runnable ()
    {
      @Override
      public void run ()
      {
        completionCallback_.onFail (failure_);
      }
    });
  }

  /**
   * Notify the task manager it is done. This will signal the task manager to run
   * the CompletionCallback on its own thread.
   */
  protected synchronized void done ()
  {
    if (!this.isDone ())
      throw new IllegalStateException ("Task manager is not done");

    if (!this.isCancelled_ && this.failure_ == null)
    {
      // We can execute the completion callback since everything is done and there
      // are not errors, and the tasks were not cancelled.

      this.executor_.execute (new Runnable ()
      {
        @Override
        public void run ()
        {
          completionCallback_.onComplete (result_);
        }
      });
    }
  }

  protected synchronized boolean canContinue ()
  {
    return this.failure_ == null && !this.isCancelled_;
  }
}