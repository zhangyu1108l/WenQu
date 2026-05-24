import { defineStore } from 'pinia';
import * as documentApi from '../api/document';

const toPageData = (data) => {
  if (Array.isArray(data)) {
    return {
      list: data,
      total: data.length
    };
  }

  const list = data?.records || data?.list || data?.items || [];

  return {
    list,
    total: data?.total ?? data?.totalCount ?? list.length
  };
};

export const useDocumentStore = defineStore('document', {
  state: () => ({
    docList: [],
    total: 0,
    uploadingTasks: new Map()
  }),

  actions: {
    async loadDocList(params) {
      const data = await documentApi.getDocList(params);
      const pageData = toPageData(data);

      this.docList = pageData.list;
      this.total = pageData.total;

      return pageData;
    },

    startUploadTask(docId, taskId) {
      this.uploadingTasks.set(Number(taskId), {
        docId: Number(docId),
        progress: 0,
        status: 'PENDING'
      });
    },

    updateTaskProgress(taskId, status, progress) {
      const task = this.uploadingTasks.get(Number(taskId));

      if (!task) {
        return;
      }

      this.uploadingTasks.set(Number(taskId), {
        ...task,
        status,
        progress
      });
    },

    removeTask(taskId) {
      this.uploadingTasks.delete(Number(taskId));
    }
  }
});
