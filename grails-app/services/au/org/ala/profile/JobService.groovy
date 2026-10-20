package au.org.ala.profile

import au.org.ala.profile.util.JobType

class JobService extends BaseDataAccessService {

    UserService userService

    List<Job> listPendingJobs(JobType type) {
        Job.withCriteria {
            if (type) {
                eq "jobType", type
            }

            order('attempts', "asc")
            order('createdDate', "desc")
        }
    }

    Job createJob(JobType type, Map json) {
        String user = userService.getCurrentUserDetails()?.userName ?: json.params?.email ?: "Unknown user"

        Job job = new Job(jobId: UUID.randomUUID().toString(), attempt: 0, params: json.params, jobType: type, userEmail: user)

        boolean success = save job

        if (success) {
            job
        } else {
            null
        }
    }

    Job updateJob(String jobId, Map json) {
        checkArgument jobId

        Job job = Job.findByJobId(jobId)
        checkState job

        job.attempt = json.attempt
        job.error = json.error
        job.params = json.params
        job.startDate = json.startDate

        boolean success = save job

        if (success) {
            job
        } else {
            null
        }
    }

    boolean deleteJob(String jobId) {
        checkArgument jobId

        Job job = Job.findByJobId(jobId)
        checkState job

        delete job
    }
}
